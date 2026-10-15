package com.rivers.nba.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.rivers.core.client.WebClientUtil;
import com.rivers.core.vo.ResultVO;
import com.rivers.nba.GetPlayerStatLeadersReq;
import com.rivers.nba.GetPlayerStatLeadersRes;
import com.rivers.nba.GetTeamStandingsReq;
import com.rivers.nba.GetTeamStandingsRes;
import com.rivers.nba.PlayerStatLeaderInfo;
import com.rivers.nba.TeamStandingInfo;
import com.rivers.nba.entity.PlayerSeasonStat;
import com.rivers.nba.entity.TeamStanding;
import com.rivers.nba.repository.PlayerRepository;
import com.rivers.nba.repository.PlayerSeasonStatsRepository;
import com.rivers.nba.repository.TeamStandingsRepository;
import com.rivers.nba.service.IRankingService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * <p>
 * 排行榜 服务实现类（数据源 SportsData，落库优先）
 * 排名数据由定时任务（syncRankingsTask）每天拉取两次落库，展示接口直接读库；
 * 库内无数据时才实时拉取上游（含赛季回落与本地快照兜底），拿到数据后异步落库。
 * 球队排名自算东西部/分区名次（上游 ConferenceRank/DivisionRank 字段不可信）；
 * 球员数据排名按场均数值排序，命中率由命中/出手自算（上游字段对部分球员不自洽）。
 * 当前赛季尚无比赛数据时自动回落到上一赛季（库内有历史赛季时读库回落，避免无效上游调用）。
 * </p>
 *
 * @author rivers
 * @since 2026-10-15
 */
@Slf4j
@Service
public class RankingServiceImpl implements IRankingService {

    /**
     * 上游赛季标记为结束年份（2027 = 2026-27 赛季），7 月后进入新赛季
     */
    private static final int SEASON_SWITCH_MONTH = 7;
    private static final long CACHE_MILLIS = 10 * 60 * 1000L;
    private static final int DEFAULT_LEADER_LIMIT = 20;
    private static final int MAX_LEADER_LIMIT = 50;
    /**
     * 进入榜单的最少场次 = 赛季最高场次的 25%（且不低于 5 场），过滤极小样本
     */
    private static final double QUALIFY_GAMES_RATIO = 0.25;
    private static final int MIN_QUALIFY_GAMES = 5;
    private static final List<String> SUPPORTED_STATS = List.of(
            "points", "rebounds", "assists", "steals", "blocks", "threePointers", "fieldGoalPercentage");

    /**
     * 球队排名排序：胜率 → 胜场 → 负场 → 队名缩写，保证同名次判定稳定
     */
    private static final Comparator<StandingRow> STANDING_ORDER = Comparator
            .comparingDouble((StandingRow row) -> row.winPct).reversed()
            .thenComparing(Comparator.comparingInt((StandingRow row) -> row.wins).reversed())
            .thenComparingInt(row -> row.losses)
            .thenComparing(row -> row.key);

    @Value("${nba.key}")
    private String nbaKey;

    @Value("${nba.url}")
    private String nbaUrl;

    @Value("${nba.stats-url:https://api.sportsdata.io/v3/nba/stats/json/}")
    private String nbaStatsUrl;

    /**
     * 上游快照目录：成功取数后落盘，配额耗尽/上游故障时兜底最后一版数据
     */
    @Value("${nba.snapshot-dir:.cache/nba-ranking}")
    private String snapshotDir;

    private final AtomicReference<ResolvedJson> standingsCache = new AtomicReference<>();

    private final AtomicReference<ResolvedJson> statsCache = new AtomicReference<>();

    private final PlayerRepository playerRepository;

    private final TeamStandingsRepository teamStandingsRepository;

    private final PlayerSeasonStatsRepository playerSeasonStatsRepository;

    private final WebClientUtil webClientUtil;

    public RankingServiceImpl(PlayerRepository playerRepository, TeamStandingsRepository teamStandingsRepository,
                              PlayerSeasonStatsRepository playerSeasonStatsRepository, WebClientUtil webClientUtil) {
        this.playerRepository = playerRepository;
        this.teamStandingsRepository = teamStandingsRepository;
        this.playerSeasonStatsRepository = playerSeasonStatsRepository;
        this.webClientUtil = webClientUtil;
    }

    @Override
    public Mono<ResultVO<GetTeamStandingsRes>> getTeamStandings(GetTeamStandingsReq req) {
        int requested = resolveSeason(req.getSeason());
        return loadStandings(requested)
                .map(data -> ResultVO.ok(buildStandingsRes(data)))
                .onErrorResume(e -> {
                    log.error("获取球队排名失败: {}", e.getMessage());
                    return Mono.just(ResultVO.ok(GetTeamStandingsRes.newBuilder().setSeason(requested).build()));
                });
    }

    @Override
    public Mono<ResultVO<GetPlayerStatLeadersRes>> getPlayerStatLeaders(GetPlayerStatLeadersReq req) {
        String statType = StringUtils.trimToEmpty(req.getStatType());
        if (!SUPPORTED_STATS.contains(statType)) {
            return Mono.just(ResultVO.fail("不支持的统计类型: " + statType));
        }
        int requested = resolveSeason(req.getSeason());
        int limit = resolveLimit(req.getLimit());
        return loadPlayerStats(requested)
                .flatMap(data -> buildLeadersRes(data, statType, limit))
                .map(ResultVO::ok)
                .onErrorResume(e -> {
                    log.error("获取球员数据排名失败: {}", e.getMessage());
                    return Mono.just(ResultVO.ok(GetPlayerStatLeadersRes.newBuilder()
                            .setSeason(requested).setStatType(statType).build()));
                });
    }

    /**
     * 同步排名数据落库（定时任务入口）：球队排名与球员统计分别拉取并替换库内对应赛季数据
     */
    @Override
    public Mono<ResultVO<Void>> syncRankings() {
        int season = resolveSeason(0);
        return fetchStandings(season)
                .flatMap(data -> saveStandings(data.resolvedSeason(), data.rows()))
                .then(fetchPlayerStats(season)
                        .flatMap(data -> savePlayerStats(data.resolvedSeason(), data.rows())))
                .then(Mono.just(ResultVO.<Void>ok()))
                .onErrorResume(e -> {
                    log.error("同步排名数据失败: {}", e.getMessage());
                    return Mono.just(ResultVO.<Void>fail("同步排名数据失败: " + e.getMessage()));
                });
    }

    /**
     * 加载球队排名：读库优先（指定赛季 → 库内最新赛季回落），库内无任何数据时实时拉取并异步落库
     */
    private Mono<StandingsData> loadStandings(int requested) {
        return readStandings(requested)
                .switchIfEmpty(Mono.defer(() -> fetchStandings(requested)
                        .map(data -> {
                            // 实时拉到的数据异步落库，后续请求直接读库
                            saveStandingsAsync(data.resolvedSeason(), data.rows());
                            return data;
                        })));
    }

    /**
     * 读库：指定赛季有数据直接返回；无数据且库内存在更早赛季时回落（赛季切换窗口，避免打上游）
     */
    private Mono<StandingsData> readStandings(int requested) {
        return teamStandingsRepository.findBySeason(requested)
                .map(RankingServiceImpl::toStandingRow)
                .collectList()
                .flatMap(rows -> {
                    if (!rows.isEmpty()) {
                        return Mono.just(new StandingsData(requested, rows));
                    }
                    return teamStandingsRepository.latestSeason()
                            .filter(latest -> latest > 0 && latest < requested)
                            .flatMap(latest -> teamStandingsRepository.findBySeason(latest)
                                    .map(RankingServiceImpl::toStandingRow)
                                    .collectList()
                                    .map(prevRows -> new StandingsData(latest, prevRows)));
                });
    }

    private Mono<StandingsData> fetchStandings(int requested) {
        return loadJson(standingsCache, "standings", nbaUrl + "Standings/", requested,
                RankingServiceImpl::hasPlayedGames, "球队排名")
                .map(resolved -> new StandingsData(resolved.resolvedSeason(), parseStandings(resolved.json())));
    }

    private Mono<LeadersData> loadPlayerStats(int requested) {
        return readPlayerStats(requested)
                .switchIfEmpty(Mono.defer(() -> fetchPlayerStats(requested)
                        .map(data -> {
                            savePlayerStatsAsync(data.resolvedSeason(), data.rows());
                            return data;
                        })));
    }

    private Mono<LeadersData> readPlayerStats(int requested) {
        return playerSeasonStatsRepository.findBySeason(requested)
                .map(RankingServiceImpl::toPlayerStatRow)
                .collectList()
                .flatMap(rows -> {
                    if (!rows.isEmpty()) {
                        return Mono.just(new LeadersData(requested, rows));
                    }
                    return playerSeasonStatsRepository.latestSeason()
                            .filter(latest -> latest > 0 && latest < requested)
                            .flatMap(latest -> playerSeasonStatsRepository.findBySeason(latest)
                                    .map(RankingServiceImpl::toPlayerStatRow)
                                    .collectList()
                                    .map(prevRows -> new LeadersData(latest, prevRows)));
                });
    }

    private Mono<LeadersData> fetchPlayerStats(int requested) {
        return loadJson(statsCache, "playerStats", nbaStatsUrl + "PlayerSeasonStats/", requested,
                RankingServiceImpl::hasStatsGames, "球员统计")
                .map(resolved -> new LeadersData(resolved.resolvedSeason(), parsePlayerStats(resolved.json())));
    }

    private void saveStandingsAsync(int season, List<StandingRow> rows) {
        saveStandings(season, rows)
                .doOnError(e -> log.warn("球队排名落库失败: {}", e.getMessage()))
                .onErrorResume(e -> Mono.empty())
                .subscribe();
    }

    private void savePlayerStatsAsync(int season, List<PlayerStatRow> rows) {
        savePlayerStats(season, rows)
                .doOnError(e -> log.warn("球员统计落库失败: {}", e.getMessage()))
                .onErrorResume(e -> Mono.empty())
                .subscribe();
    }

    private Mono<Void> saveStandings(int season, List<StandingRow> rows) {
        if (rows.isEmpty()) {
            return Mono.empty();
        }
        return Flux.fromIterable(rows)
                .concatMap(row -> teamStandingsRepository.upsert(toStandingEntity(season, row)))
                .then();
    }

    private Mono<Void> savePlayerStats(int season, List<PlayerStatRow> rows) {
        if (rows.isEmpty()) {
            return Mono.empty();
        }
        return Flux.fromIterable(rows)
                .concatMap(row -> playerSeasonStatsRepository.upsert(toPlayerStatEntity(season, row)))
                .then();
    }

    /**
     * 加载赛季 JSON：命中缓存直接返回；该赛季尚无比赛数据时自动回落到上一赛季；
     * 上游失败（配额耗尽/网络故障）时兜底本地快照，避免页面空白。
     * 回落/快照结果按请求赛季缓存，避免每次请求都先打一次无效上游。
     */
    private Mono<ResolvedJson> loadJson(AtomicReference<ResolvedJson> cache, String snapshotName, String urlPrefix,
                                        int season, Predicate<String> hasData, String label) {
        ResolvedJson cached = cache.get();
        if (cached != null && cached.requestedSeason() == season
                && System.currentTimeMillis() - cached.time() < CACHE_MILLIS) {
            return Mono.just(cached);
        }
        return webClientUtil.get(urlPrefix + season + "?key=" + nbaKey)
                .flatMap(json -> {
                    if (hasData.test(json) || season <= 1) {
                        cacheAndSnapshot(cache, snapshotName, season, season, json);
                        return Mono.just(cache.get());
                    }
                    log.info("{}赛季 {} 暂无比赛数据，回落到赛季 {}", label, season, season - 1);
                    return webClientUtil.get(urlPrefix + (season - 1) + "?key=" + nbaKey)
                            .map(prevJson -> {
                                cacheAndSnapshot(cache, snapshotName, season, season - 1, prevJson);
                                return cache.get();
                            })
                            .onErrorResume(e -> fallbackToSnapshot(cache, snapshotName, season, label, e));
                })
                .onErrorResume(e -> fallbackToSnapshot(cache, snapshotName, season, label, e));
    }

    private void cacheAndSnapshot(AtomicReference<ResolvedJson> cache, String snapshotName,
                                  int requestedSeason, int resolvedSeason, String json) {
        cache.set(new ResolvedJson(requestedSeason, resolvedSeason, json, System.currentTimeMillis()));
        Path dir = Path.of(snapshotDir);
        Path file = dir.resolve(snapshotName + "-" + resolvedSeason + ".json");
        Mono.fromRunnable(() -> {
                    try {
                        Files.createDirectories(dir);
                        Files.writeString(file, json);
                    } catch (IOException e) {
                        log.warn("写入上游快照 {} 失败: {}", file, e.getMessage());
                    }
                })
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();
    }

    /**
     * 上游失败兜底：优先请求赛季快照，其次上一赛季；都无快照则抛出原始异常
     */
    private Mono<ResolvedJson> fallbackToSnapshot(AtomicReference<ResolvedJson> cache, String snapshotName,
                                                  int season, String label, Throwable cause) {
        return Mono.fromCallable(() -> readSnapshot(snapshotName, season))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(hit -> hit
                        .<Mono<ResolvedJson>>map(h -> {
                            log.warn("{}上游获取失败（{}），使用本地快照（赛季 {}）",
                                    label, cause.getMessage(), h.season());
                            cache.set(new ResolvedJson(season, h.season(), h.json(), System.currentTimeMillis()));
                            return Mono.just(cache.get());
                        })
                        .orElseGet(() -> Mono.error(cause)));
    }

    private Optional<SnapshotHit> readSnapshot(String snapshotName, int requestedSeason) {
        Path dir = Path.of(snapshotDir);
        for (int season : new int[]{requestedSeason, requestedSeason - 1}) {
            Path file = dir.resolve(snapshotName + "-" + season + ".json");
            try {
                if (Files.isReadable(file)) {
                    String json = Files.readString(file);
                    if (StringUtils.isNotBlank(json)) {
                        return Optional.of(new SnapshotHit(season, json));
                    }
                }
            } catch (IOException e) {
                log.warn("读取上游快照 {} 失败: {}", file, e.getMessage());
            }
        }
        return Optional.empty();
    }

    private static GetTeamStandingsRes buildStandingsRes(StandingsData data) {
        List<StandingRow> rows = new ArrayList<>(data.rows());
        assignRanks(rows);
        // 库内/上游行序非联盟名次序，输出前按联盟+联盟名次排序（分区名次与联盟名次同序，同一比较器按子集排序）
        rows.sort(Comparator.comparing((StandingRow row) -> row.conference)
                .thenComparingInt(row -> row.conferenceRank));
        return GetTeamStandingsRes.newBuilder()
                .setSeason(data.resolvedSeason())
                .addAllStandings(rows.stream().map(RankingServiceImpl::toStandingInfo).toList())
                .build();
    }

    private static List<StandingRow> parseStandings(String json) {
        JSONArray array = JSON.parseArray(json);
        if (array == null) {
            return List.of();
        }
        List<StandingRow> rows = new ArrayList<>(array.size());
        for (int i = 0; i < array.size(); i++) {
            JSONObject o = array.getJSONObject(i);
            StandingRow row = new StandingRow();
            row.teamId = o.getIntValue("TeamID");
            row.key = StringUtils.defaultString(o.getString("Key"));
            row.city = StringUtils.defaultString(o.getString("City"));
            row.name = StringUtils.defaultString(o.getString("Name"));
            row.conference = StringUtils.defaultString(o.getString("Conference"));
            row.division = StringUtils.defaultString(o.getString("Division"));
            row.wins = o.getIntValue("Wins");
            row.losses = o.getIntValue("Losses");
            row.winPct = winPctOf(row.wins, row.losses);
            row.gamesBack = o.getDoubleValue("GamesBack");
            row.lastTenWins = o.getIntValue("LastTenWins");
            row.lastTenLosses = o.getIntValue("LastTenLosses");
            row.streakDescription = StringUtils.defaultString(o.getString("StreakDescription"));
            row.pointsPerGameFor = o.getDoubleValue("PointsPerGameFor");
            row.pointsPerGameAgainst = o.getDoubleValue("PointsPerGameAgainst");
            rows.add(row);
        }
        return rows;
    }

    private static StandingRow toStandingRow(TeamStanding entity) {
        StandingRow row = new StandingRow();
        row.teamId = defaultInt(entity.getTeamId());
        row.key = defaultString(entity.getTeamKey());
        row.city = defaultString(entity.getCity());
        row.name = defaultString(entity.getName());
        row.conference = defaultString(entity.getConference());
        row.division = defaultString(entity.getDivision());
        row.wins = defaultInt(entity.getWins());
        row.losses = defaultInt(entity.getLosses());
        row.winPct = winPctOf(row.wins, row.losses);
        row.gamesBack = defaultDouble(entity.getGamesBack());
        row.lastTenWins = defaultInt(entity.getLastTenWins());
        row.lastTenLosses = defaultInt(entity.getLastTenLosses());
        row.streakDescription = defaultString(entity.getStreakDescription());
        row.pointsPerGameFor = defaultDouble(entity.getPointsPerGameFor());
        row.pointsPerGameAgainst = defaultDouble(entity.getPointsPerGameAgainst());
        return row;
    }

    private static TeamStanding toStandingEntity(int season, StandingRow row) {
        TeamStanding entity = new TeamStanding();
        entity.setSeason(season);
        entity.setTeamId(row.teamId);
        entity.setTeamKey(row.key);
        entity.setCity(row.city);
        entity.setName(row.name);
        entity.setConference(row.conference);
        entity.setDivision(row.division);
        entity.setWins(row.wins);
        entity.setLosses(row.losses);
        entity.setGamesBack(row.gamesBack);
        entity.setLastTenWins(row.lastTenWins);
        entity.setLastTenLosses(row.lastTenLosses);
        entity.setStreakDescription(row.streakDescription);
        entity.setPointsPerGameFor(row.pointsPerGameFor);
        entity.setPointsPerGameAgainst(row.pointsPerGameAgainst);
        return entity;
    }

    /**
     * 胜率自算（上游 Percentage 对 0 场次的球队返回 1，自算避免误排），保留三位小数
     */
    private static double winPctOf(int wins, int losses) {
        int games = wins + losses;
        return games > 0 ? Math.round(wins * 1000.0 / games) / 1000.0 : 0.0;
    }

    private static void assignRanks(List<StandingRow> rows) {
        assignRank(rows, row -> row.conference, (row, rank) -> row.conferenceRank = rank);
        assignRank(rows, row -> row.division, (row, rank) -> row.divisionRank = rank);
    }

    private static void assignRank(List<StandingRow> rows, Function<StandingRow, String> groupKey,
                                   BiConsumer<StandingRow, Integer> rankSetter) {
        Map<String, List<StandingRow>> grouped = rows.stream()
                .filter(row -> StringUtils.isNotBlank(groupKey.apply(row)))
                .collect(Collectors.groupingBy(groupKey));
        grouped.values().forEach(group -> {
            group.sort(STANDING_ORDER);
            for (int i = 0; i < group.size(); i++) {
                rankSetter.accept(group.get(i), i + 1);
            }
        });
    }

    private static TeamStandingInfo toStandingInfo(StandingRow row) {
        return TeamStandingInfo.newBuilder()
                .setTeamId(row.teamId)
                .setKey(row.key)
                .setCity(row.city)
                .setName(row.name)
                .setConference(row.conference)
                .setDivision(row.division)
                .setWins(row.wins)
                .setLosses(row.losses)
                .setWinPct(row.winPct)
                .setGamesBack(row.gamesBack)
                .setConferenceRank(row.conferenceRank)
                .setDivisionRank(row.divisionRank)
                .setLastTenWins(row.lastTenWins)
                .setLastTenLosses(row.lastTenLosses)
                .setStreakDescription(row.streakDescription)
                .setPointsPerGameFor(row.pointsPerGameFor)
                .setPointsPerGameAgainst(row.pointsPerGameAgainst)
                .build();
    }

    private Mono<GetPlayerStatLeadersRes> buildLeadersRes(LeadersData data, String statType, int limit) {
        List<PlayerStatLeaderInfo> leaders = rankLeaders(data.rows(), statType, limit);
        return Flux.fromIterable(leaders)
                .concatMap(item -> playerRepository.photoByPlayerId(item.getPlayerId())
                        .defaultIfEmpty("")
                        .map(photo -> item.toBuilder().setPhotoUrl(photo).build()))
                .collectList()
                .map(list -> GetPlayerStatLeadersRes.newBuilder()
                        .setSeason(data.resolvedSeason())
                        .setStatType(statType)
                        .addAllLeaders(list)
                        .build());
    }

    /**
     * 按统计类型对全量球员统计排序取前 limit（出场场次不足者过滤）
     */
    private static List<PlayerStatLeaderInfo> rankLeaders(List<PlayerStatRow> rows, String statType, int limit) {
        if (rows.isEmpty()) {
            return List.of();
        }
        int maxGames = rows.stream().mapToInt(PlayerStatRow::games).max().orElse(0);
        int minGames = Math.max(MIN_QUALIFY_GAMES, (int) Math.ceil(maxGames * QUALIFY_GAMES_RATIO));
        return rows.stream()
                .filter(row -> row.games() >= minGames)
                .map(row -> new LeaderCandidate(row, statValue(statType, row)))
                .sorted(Comparator.comparingDouble(LeaderCandidate::value).reversed())
                .limit(limit)
                .map(candidate -> PlayerStatLeaderInfo.newBuilder()
                        .setPlayerId(candidate.row().playerId())
                        .setName(candidate.row().name())
                        .setTeam(candidate.row().team())
                        .setPosition(candidate.row().position())
                        .setGames(candidate.row().games())
                        .setValue(candidate.value())
                        .build())
                .toList();
    }

    private static List<PlayerStatRow> parsePlayerStats(String json) {
        JSONArray array = JSON.parseArray(json);
        if (array == null) {
            return List.of();
        }
        List<PlayerStatRow> rows = new ArrayList<>(array.size());
        for (int i = 0; i < array.size(); i++) {
            JSONObject o = array.getJSONObject(i);
            int games = o.getIntValue("Games");
            if (games <= 0) {
                continue;
            }
            rows.add(new PlayerStatRow(
                    o.getLongValue("PlayerID"),
                    StringUtils.defaultString(o.getString("Name")),
                    StringUtils.defaultString(o.getString("Team")),
                    StringUtils.defaultString(o.getString("Position")),
                    games,
                    o.getDoubleValue("Points"),
                    o.getDoubleValue("Rebounds"),
                    o.getDoubleValue("Assists"),
                    o.getDoubleValue("Steals"),
                    o.getDoubleValue("BlockedShots"),
                    o.getDoubleValue("ThreePointersMade"),
                    o.getDoubleValue("FieldGoalsMade"),
                    o.getDoubleValue("FieldGoalsAttempted")));
        }
        return rows;
    }

    private static PlayerStatRow toPlayerStatRow(PlayerSeasonStat entity) {
        return new PlayerStatRow(
                defaultLong(entity.getPlayerId()),
                defaultString(entity.getPlayerName()),
                defaultString(entity.getTeam()),
                defaultString(entity.getPosition()),
                defaultInt(entity.getGames()),
                defaultDouble(entity.getPoints()),
                defaultDouble(entity.getRebounds()),
                defaultDouble(entity.getAssists()),
                defaultDouble(entity.getSteals()),
                defaultDouble(entity.getBlockedShots()),
                defaultDouble(entity.getThreePointersMade()),
                defaultDouble(entity.getFieldGoalsMade()),
                defaultDouble(entity.getFieldGoalsAttempted()));
    }

    private static PlayerSeasonStat toPlayerStatEntity(int season, PlayerStatRow row) {
        PlayerSeasonStat entity = new PlayerSeasonStat();
        entity.setSeason(season);
        entity.setPlayerId(row.playerId());
        entity.setPlayerName(row.name());
        entity.setTeam(row.team());
        entity.setPosition(row.position());
        entity.setGames(row.games());
        entity.setPoints(row.points());
        entity.setRebounds(row.rebounds());
        entity.setAssists(row.assists());
        entity.setSteals(row.steals());
        entity.setBlockedShots(row.blockedShots());
        entity.setThreePointersMade(row.threePointersMade());
        entity.setFieldGoalsMade(row.fieldGoalsMade());
        entity.setFieldGoalsAttempted(row.fieldGoalsAttempted());
        return entity;
    }

    /**
     * 场均/命中率取值（statType 已在入口处白名单校验）
     */
    private static double statValue(String statType, PlayerStatRow player) {
        int games = player.games();
        if (games <= 0) {
            return 0;
        }
        return switch (statType) {
            case "points" -> perGame(player.points(), games);
            case "rebounds" -> perGame(player.rebounds(), games);
            case "assists" -> perGame(player.assists(), games);
            case "steals" -> perGame(player.steals(), games);
            case "blocks" -> perGame(player.blockedShots(), games);
            case "threePointers" -> perGame(player.threePointersMade(), games);
            case "fieldGoalPercentage" -> fieldGoalPercentage(player);
            default -> 0;
        };
    }

    private static double perGame(double total, int games) {
        return Math.round(total / games * 10) / 10.0;
    }

    /**
     * 命中率由命中/出手自算（上游 FieldGoalsPercentage 字段对部分球员与自身数据不符），保留一位小数
     */
    private static double fieldGoalPercentage(PlayerStatRow player) {
        double attempts = player.fieldGoalsAttempted();
        if (attempts <= 0) {
            return 0;
        }
        return Math.round(player.fieldGoalsMade() / attempts * 1000) / 10.0;
    }

    /**
     * 是否有球队已打比赛（新赛季未开赛时全为 0 胜 0 负，需回落上一赛季）
     */
    private static boolean hasPlayedGames(String json) {
        JSONArray array = JSON.parseArray(json);
        if (array == null) {
            return false;
        }
        for (int i = 0; i < array.size(); i++) {
            JSONObject o = array.getJSONObject(i);
            if (o.getIntValue("Wins") + o.getIntValue("Losses") > 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * 是否有球员出场记录（新赛季未开赛时统计为空，需回落上一赛季）
     */
    private static boolean hasStatsGames(String json) {
        JSONArray array = JSON.parseArray(json);
        if (array == null) {
            return false;
        }
        for (int i = 0; i < array.size(); i++) {
            if (array.getJSONObject(i).getIntValue("Games") > 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * 解析有效赛季（非正数时取当前赛季：7 月后进入新赛季）
     */
    private static int resolveSeason(int season) {
        if (season > 0) {
            return season;
        }
        LocalDate now = LocalDate.now();
        return now.getMonthValue() >= SEASON_SWITCH_MONTH ? now.getYear() + 1 : now.getYear();
    }

    /**
     * 解析有效条数（非正数取默认值，超上限截断）
     */
    private static int resolveLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_LEADER_LIMIT;
        }
        return Math.min(limit, MAX_LEADER_LIMIT);
    }

    private static String defaultString(String value) {
        if (value == null) {
            return "";
        }
        return value;
    }

    private static int defaultInt(Integer value) {
        if (value == null) {
            return 0;
        }
        return value;
    }

    private static double defaultDouble(Double value) {
        if (value == null) {
            return 0;
        }
        return value;
    }

    private static long defaultLong(Long value) {
        if (value == null) {
            return 0L;
        }
        return value;
    }

    /**
     * 球队排名可变中间行（proto 消息不可变，需先算出名次再构建）
     */
    private static final class StandingRow {

        private int teamId;

        private String key;

        private String city;

        private String name;

        private String conference;

        private String division;

        private int wins;

        private int losses;

        private double winPct;

        private double gamesBack;

        private int conferenceRank;

        private int divisionRank;

        private int lastTenWins;

        private int lastTenLosses;

        private String streakDescription;

        private double pointsPerGameFor;

        private double pointsPerGameAgainst;
    }

    /**
     * 球员统计统一中间行：库内实体与上游 JSON 均映射为此结构后计算榜单
     */
    private record PlayerStatRow(long playerId, String name, String team, String position, int games,
                                 double points, double rebounds, double assists, double steals,
                                 double blockedShots, double threePointersMade, double fieldGoalsMade,
                                 double fieldGoalsAttempted) {
    }

    /**
     * 球队排名数据集：resolvedSeason 为数据实际归属赛季（回落时不同于请求赛季）
     */
    private record StandingsData(int resolvedSeason, List<StandingRow> rows) {
    }

    /**
     * 球员统计数据集：resolvedSeason 为数据实际归属赛季（回落时不同于请求赛季）
     */
    private record LeadersData(int resolvedSeason, List<PlayerStatRow> rows) {
    }

    private record LeaderCandidate(PlayerStatRow row, double value) {
    }

    /**
     * 赛季解析结果：请求赛季无数据时 resolvedSeason 为实际取数的上一赛季
     */
    private record ResolvedJson(int requestedSeason, int resolvedSeason, String json, long time) {
    }

    /**
     * 本地快照命中结果：season 为快照实际归属赛季
     */
    private record SnapshotHit(int season, String json) {
    }
}
