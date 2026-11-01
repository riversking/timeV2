package com.rivers.nba.service.impl;

import com.rivers.core.vo.ResultVO;
import com.rivers.nba.*;
import com.rivers.nba.client.EspnRankingClient;
import com.rivers.nba.client.EspnRankingClient.EspnPlayerStat;
import com.rivers.nba.client.EspnRankingClient.EspnTeamStanding;
import com.rivers.nba.client.EspnScoreboardClient;
import com.rivers.nba.client.EspnScoreboardClient.EspnGame;
import com.rivers.nba.client.EspnScoreboardClient.EspnGameDetail;
import com.rivers.nba.client.EspnScoreboardClient.EspnPlayerLine;
import com.rivers.nba.client.EspnScoreboardClient.EspnTeamBox;
import com.rivers.nba.entity.PlayerLeagueStat;
import com.rivers.nba.entity.PlayerSeasonHistory;
import com.rivers.nba.entity.PlayerSeasonStat;
import com.rivers.nba.entity.Team;
import com.rivers.nba.entity.TeamStanding;
import com.rivers.nba.entity.TimerPlayer;
import com.rivers.nba.repository.PlayerRepository;
import com.rivers.nba.repository.PlayerSeasonHistoryRepository;
import com.rivers.nba.repository.PlayerSeasonStatsRepository;
import com.rivers.nba.repository.TeamRepository;
import com.rivers.nba.repository.TeamStandingsRepository;
import com.rivers.nba.service.ILeagueCompareService;
import com.rivers.nba.service.IRankingService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.rivers.core.util.ValueUtils.*;

/**
 * <p>
 * 排行榜 服务实现类（数据源 ESPN，落库优先）
 * 排名数据由定时任务（syncRankingsTask）每天拉取两次落库，展示接口直接读库；
 * 库内无数据时才实时拉取 ESPN（含赛季回落），拿到数据后异步落库。
 * 球队维度（teamId/城市/队名/东西部/赛区）取库内 team 表（ESPN 不提供赛区），
 * 战绩与得失分取 ESPN standings；球员统计取 ESPN byathlete，按姓名映射库内 player_id
 * 以保留头像与球员链接，未匹配到时回退 ESPN 球员 ID。
 * 球队排名自算东西部/分区名次；球员数据排名按场均数值排序，命中率由命中/出手自算。
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
    /**
     * 实时拉取的兜底缓存有效期：库内无数据时避免同一实例反复打上游
     */
    private static final long CACHE_MILLIS = 10 * 60 * 1000L;
    private static final int DEFAULT_LEADER_LIMIT = 20;
    private static final int MAX_LEADER_LIMIT = 50;
    /**
     * 进入榜单的最少场次 = 赛季最高场次的 25%（且不低于 5 场）；赛季初不足 5 场时不做门槛过滤
     */
    private static final double QUALIFY_GAMES_RATIO = 0.25;
    private static final int MIN_QUALIFY_GAMES = 5;
    private static final List<String> SUPPORTED_STATS = List.of(
            "points", "rebounds", "assists", "steals", "blocks", "threePointers", "fieldGoalPercentage");

    /**
     * 每日最佳：默认从美东今日起向前回看的天数（无完赛比赛时逐日回溯）
     */
    private static final int MAX_DAILY_LOOKBACK_DAYS = 7;
    /**
     * 球队榜固定取前 5
     */
    private static final int TEAM_LEADER_LIMIT = 5;
    /**
     * 单日命中率门槛：至少出手 5 次，避免小样本霸榜
     */
    private static final int MIN_DAILY_FG_ATTEMPTS = 5;
    /**
     * 赛季命中率门槛：至少出手 10 次，避免替补零投零中霸榜
     */
    private static final int MIN_SEASON_FG_ATTEMPTS = 10;
    /**
     * 球员详情可回溯的赛季数（含当季），历史赛季只在库内缺失时回填
     */
    private static final int SEASON_HISTORY_RANGE = 10;
    private static final ZoneId GAME_ZONE = ZoneId.of("America/New_York");
    private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    /**
     * 球队排名排序：胜率 → 胜场 → 负场 → 队名缩写，保证同名次判定稳定
     */
    private static final Comparator<StandingRow> STANDING_ORDER = Comparator
            .comparingDouble((StandingRow row) -> row.winPct).reversed()
            .thenComparing(Comparator.comparingInt((StandingRow row) -> row.wins).reversed())
            .thenComparingInt(row -> row.losses)
            .thenComparing(row -> row.key);

    private final AtomicReference<CachedStandings> standingsCache = new AtomicReference<>();

    private final AtomicReference<CachedLeaders> leadersCache = new AtomicReference<>();

    private final Map<String, CachedDaily> dailyCache = new ConcurrentHashMap<>();

    private final PlayerRepository playerRepository;

    private final TeamRepository teamRepository;

    private final TeamStandingsRepository teamStandingsRepository;

    private final PlayerSeasonStatsRepository playerSeasonStatsRepository;

    private final PlayerSeasonHistoryRepository playerSeasonHistoryRepository;

    private final ILeagueCompareService leagueCompareService;

    private final EspnRankingClient espnRankingClient;

    private final EspnScoreboardClient espnScoreboardClient;

    public RankingServiceImpl(PlayerRepository playerRepository, TeamRepository teamRepository,
                              TeamStandingsRepository teamStandingsRepository,
                              PlayerSeasonStatsRepository playerSeasonStatsRepository,
                              PlayerSeasonHistoryRepository playerSeasonHistoryRepository,
                              ILeagueCompareService leagueCompareService,
                              EspnRankingClient espnRankingClient,
                              EspnScoreboardClient espnScoreboardClient) {
        this.playerRepository = playerRepository;
        this.teamRepository = teamRepository;
        this.teamStandingsRepository = teamStandingsRepository;
        this.playerSeasonStatsRepository = playerSeasonStatsRepository;
        this.playerSeasonHistoryRepository = playerSeasonHistoryRepository;
        this.leagueCompareService = leagueCompareService;
        this.espnRankingClient = espnRankingClient;
        this.espnScoreboardClient = espnScoreboardClient;
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
     * 每日最佳：聚合指定比赛日（缺省为最近一个有完赛的比赛日）全部完赛比赛的球员与球队单场数据，
     * 按所选统计类型降序取前 limit（球队固定前 {@link #TEAM_LEADER_LIMIT}）
     */
    @Override
    public Mono<ResultVO<GetDailyLeadersRes>> getDailyLeaders(GetDailyLeadersReq req) {
        String statType = StringUtils.defaultIfBlank(StringUtils.trimToEmpty(req.getStatType()), "points");
        if (!SUPPORTED_STATS.contains(statType)) {
            return Mono.just(ResultVO.fail("不支持的统计类型: " + statType));
        }
        int limit = resolveLimit(req.getLimit());
        return loadDailyLeaders(StringUtils.trimToEmpty(req.getDate()), statType, limit)
                .map(ResultVO::ok)
                .onErrorResume(e -> {
                    log.error("获取每日最佳失败: {}", e.getMessage());
                    return Mono.just(ResultVO.ok(GetDailyLeadersRes.getDefaultInstance()));
                });
    }

    /**
     * 球员单赛季数据：读库优先（按赛季倒序）；库内无该球员任何赛季数据时
     * 实时拉取当季 ESPN 统计，按姓名匹配后落库并返回。
     * 联盟对比取同步时预计算的对比表，请求链路不做任何聚合
     */
    @Override
    public Mono<ResultVO<GetPlayerSeasonStatsRes>> getPlayerSeasonStats(GetPlayerSeasonStatsReq req) {
        long playerId = req.getPlayerId();
        return playerSeasonStatsRepository.findByPlayerId(playerId)
                .collectList()
                .flatMap(rows -> rows.isEmpty() ? fetchPlayerSeasonStats(playerId) : Mono.just(rows))
                .flatMap(rows -> buildSeasonStatsRes(playerId, rows))
                .map(ResultVO::ok)
                .onErrorResume(e -> {
                    log.error("获取球员赛季数据失败: playerId={}, error={}", playerId, e.getMessage());
                    return Mono.just(ResultVO.ok(GetPlayerSeasonStatsRes.getDefaultInstance()));
                });
    }

    /**
     * 组装赛季数据：对比行来自预计算表，历年数据来自 player_season_history，
     * 两者均为库内读取，按赛季归组后填充
     */
    private Mono<GetPlayerSeasonStatsRes> buildSeasonStatsRes(long playerId, List<PlayerSeasonStat> rows) {
        Mono<Map<Integer, Collection<PlayerLeagueStat>>> compareMono = leagueCompareService.findByPlayerId(playerId)
                .collectMultimap(row -> defaultInt(row.getSeason()), Function.identity());
        Mono<List<PlayerSeasonHistoryInfo>> historyMono = playerSeasonHistoryRepository.findByPlayerId(playerId)
                .map(RankingServiceImpl::toHistoryInfo)
                .collectList()
                .onErrorResume(e -> {
                    log.warn("读取球员历年数据失败: playerId={}, error={}", playerId, e.getMessage());
                    return Mono.just(List.of());
                });
        return Mono.zip(compareMono, historyMono)
                .map(data -> GetPlayerSeasonStatsRes.newBuilder()
                        .addAllSeasons(rows.stream()
                                .map(row -> toSeasonInfo(row,
                                        data.getT1().getOrDefault(defaultInt(row.getSeason()), List.of())))
                                .toList())
                        .addAllHistory(data.getT2())
                        .build());
    }

    /**
     * 库内无数据时的兜底：拉取当季全联盟统计，按归一姓名匹配该球员后落库（只 upsert 本人行，不整季替换）
     */
    private Mono<List<PlayerSeasonStat>> fetchPlayerSeasonStats(long playerId) {
        return playerRepository.findByPlayerId(playerId)
                .flatMap(player -> {
                    int season = resolveSeason(0);
                    String name = normalizeName(player.getFirstName() + " " + player.getLastName());
                    return espnRankingClient.fetchPlayerStats(season)
                            .flatMap(rows -> {
                                List<PlayerStatRow> matched = rows.stream()
                                        .filter(row -> name.equals(normalizeName(row.athlete().name())))
                                        .map(row -> new PlayerStatRow(playerId, row.athlete().name(), row.team(),
                                                StringUtils.defaultIfBlank(player.getPosition(), row.position()),
                                                row.games(), row.points(), row.rebounds(), row.assists(),
                                                row.steals(), row.blocks(), row.threePointersMade(),
                                                row.fieldGoalsMade(), row.fieldGoalsAttempted()))
                                        .toList();
                                if (matched.isEmpty()) {
                                    return Mono.just(List.<PlayerSeasonStat>of());
                                }
                                return savePlayerSeasonRows(season, matched)
                                        .then(leagueCompareService.rebuildSeason(season))
                                        .thenReturn(matched.stream()
                                                .map(row -> toPlayerStatEntity(season, row))
                                                .toList());
                            });
                })
                .defaultIfEmpty(List.of());
    }

    /**
     * 只写入单个球员的赛季行（兜底链路，不能整季替换，避免覆盖同赛季其他球员）
     */
    private Mono<Void> savePlayerSeasonRows(int season, List<PlayerStatRow> rows) {
        return Flux.fromIterable(rows)
                .concatMap(row -> playerSeasonStatsRepository.upsert(toPlayerStatEntity(season, row)))
                .then()
                .doOnError(e -> log.warn("球员赛季数据落库失败: {}", e.getMessage()))
                .onErrorResume(e -> Mono.empty());
    }

    /**
     * 同步排名数据落库（定时任务入口）：球队排名与球员统计分别拉取并整季替换库内数据
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
     * 同步球员赛季数据落库（定时任务入口）：当季每次整季刷新；历史赛季仅在库内缺失时回填，
     * 回填完成后不再产生上游请求。历史赛季按由近及远补齐，保证近期赛季最先可用
     */
    @Override
    public Mono<ResultVO<Void>> syncPlayerSeasonStats() {
        int current = resolveSeason(0);
        int start = Math.max(1, current - SEASON_HISTORY_RANGE + 1);
        int historyCount = Math.max(0, current - start);
        return loadPlayerIndex()
                .flatMap(index -> syncSeason(current, index)
                        .thenMany(Flux.range(0, historyCount)
                                .concatMap(offset -> backfillSeason(current - 1 - offset, index)))
                        .then(Mono.just(ResultVO.<Void>ok())))
                .onErrorResume(e -> {
                    log.error("同步球员赛季数据失败: {}", e.getMessage());
                    return Mono.just(ResultVO.<Void>fail("同步球员赛季数据失败: " + e.getMessage()));
                });
    }

    /**
     * 当季整季刷新（ESPN 拉取失败时保留库内旧数据）
     */
    private Mono<Void> syncSeason(int season, Map<String, LocalPlayer> index) {
        return fetchAndSaveSeason(season, index)
                .doOnError(e -> log.warn("球员赛季数据同步失败: season={}, error={}", season, e.getMessage()))
                .onErrorResume(e -> Mono.empty());
    }

    /**
     * 历史赛季回填：库内已有该赛季数据则跳过
     */
    private Mono<Void> backfillSeason(int season, Map<String, LocalPlayer> index) {
        return playerSeasonStatsRepository.countBySeason(season)
                .flatMap(count -> count > 0 ? Mono.empty() : fetchAndSaveSeason(season, index))
                .doOnError(e -> log.warn("球员赛季数据回填失败: season={}, error={}", season, e.getMessage()))
                .onErrorResume(e -> Mono.empty());
    }

    private Mono<Void> fetchAndSaveSeason(int season, Map<String, LocalPlayer> index) {
        return espnRankingClient.fetchPlayerStats(season)
                .flatMap(rows -> {
                    if (rows.isEmpty()) {
                        return Mono.empty();
                    }
                    log.info("同步球员赛季数据: season={}, rows={}", season, rows.size());
                    return savePlayerStats(season, toPlayerStatRows(rows, index));
                });
    }

    private static PlayerSeasonStatInfo toSeasonInfo(PlayerSeasonStat entity,
                                                     Collection<PlayerLeagueStat> compareRows) {
        int games = defaultInt(entity.getGames());
        double fieldGoalsMade = defaultDouble(entity.getFieldGoalsMade());
        double fieldGoalsAttempted = defaultDouble(entity.getFieldGoalsAttempted());
        return PlayerSeasonStatInfo.newBuilder()
                .setSeason(defaultInt(entity.getSeason()))
                .setTeam(defaultString(entity.getTeam()))
                .setPosition(defaultString(entity.getPosition()))
                .setGames(games)
                .setAvgPoints(perGame(entity.getPoints(), games))
                .setAvgRebounds(perGame(entity.getRebounds(), games))
                .setAvgAssists(perGame(entity.getAssists(), games))
                .setAvgSteals(perGame(entity.getSteals(), games))
                .setAvgBlocks(perGame(entity.getBlockedShots(), games))
                .setAvgThreePointersMade(perGame(entity.getThreePointersMade(), games))
                .setAvgFieldGoalsMade(perGame(fieldGoalsMade, games))
                .setAvgFieldGoalsAttempted(perGame(fieldGoalsAttempted, games))
                .setFieldGoalPct(shootingPercentage(fieldGoalsMade, fieldGoalsAttempted))
                .addAllLeagueCompare(compareRows.stream().map(RankingServiceImpl::toLeagueCompareInfo).toList())
                .build();
    }

    /**
     * 历年数据落库为赛季合计，出参统一换算为场均与百分比（命中率由命中/出手自算）
     */
    private static PlayerSeasonHistoryInfo toHistoryInfo(PlayerSeasonHistory entity) {
        int games = defaultInt(entity.getGames());
        double fieldGoalsMade = defaultDouble(entity.getFieldGoalsMade());
        double fieldGoalsAttempted = defaultDouble(entity.getFieldGoalsAttempted());
        double threePointersMade = defaultDouble(entity.getThreePointersMade());
        double threePointersAttempted = defaultDouble(entity.getThreePointersAttempted());
        double freeThrowsMade = defaultDouble(entity.getFreeThrowsMade());
        double freeThrowsAttempted = defaultDouble(entity.getFreeThrowsAttempted());
        return PlayerSeasonHistoryInfo.newBuilder()
                .setSeason(defaultInt(entity.getSeason()))
                .setSeasonType(defaultInt(entity.getSeasonType()))
                .setTeam(defaultString(entity.getTeam()))
                .setGames(games)
                .setAvgPoints(perGame(entity.getPoints(), games))
                .setAvgMinutes(perGame(entity.getMinutes(), games))
                .setFieldGoalPct(shootingPercentage(fieldGoalsMade, fieldGoalsAttempted))
                .setAvgFieldGoalsAttempted(perGame(fieldGoalsAttempted, games))
                .setThreePointPct(shootingPercentage(threePointersMade, threePointersAttempted))
                .setAvgThreePointersAttempted(perGame(threePointersAttempted, games))
                .setFreeThrowPct(shootingPercentage(freeThrowsMade, freeThrowsAttempted))
                .setAvgFreeThrowsAttempted(perGame(freeThrowsAttempted, games))
                .setAvgRebounds(perGame(entity.getRebounds(), games))
                .setAvgAssists(perGame(entity.getAssists(), games))
                .setAvgSteals(perGame(entity.getSteals(), games))
                .setAvgBlocks(perGame(entity.getBlockedShots(), games))
                .setAvgFouls(perGame(entity.getFouls(), games))
                .build();
    }

    private static LeagueCompareInfo toLeagueCompareInfo(PlayerLeagueStat row) {
        return LeagueCompareInfo.newBuilder()
                .setKey(defaultString(row.getStatKey()))
                .setLabel(defaultString(row.getStatLabel()))
                .setPlayerValue(defaultDouble(row.getPlayerValue()))
                .setLeagueAvg(defaultDouble(row.getLeagueAvg()))
                .setLeagueMax(defaultDouble(row.getLeagueMax()))
                .setRank(defaultInt(row.getRankNo()))
                .setTotalPlayers(defaultInt(row.getTotalPlayers()))
                .build();
    }

    /**
     * 加载每日最佳：按比赛日缓存 10 分钟（完赛后的比赛数据当日不再变化），排序在出参时按统计类型计算
     */
    private Mono<GetDailyLeadersRes> loadDailyLeaders(String requestedDate, String statType, int limit) {
        CachedDaily cached = dailyCache.get(requestedDate);
        if (cached != null && System.currentTimeMillis() - cached.time() < CACHE_MILLIS) {
            return Mono.just(buildDailyRes(cached.date(), cached.rows(), cached.teams(), statType, limit));
        }
        LocalDate explicitDay = parseDay(requestedDate);
        LocalDate start = explicitDay != null ? explicitDay : LocalDate.now(GAME_ZONE);
        return fetchDailyBoxes(start, explicitDay != null, 0)
                .flatMap(dayBoxes -> dayBoxes.boxes().isEmpty()
                        ? Mono.just(new DailyData(dayBoxes.day(), List.of(), List.of()))
                        : loadPlayerIndex().map(index -> new DailyData(dayBoxes.day(),
                                toDailyRows(dayBoxes.boxes(), index), toDailyTeams(dayBoxes.boxes()))))
                .doOnNext(data -> {
                    if (!data.rows().isEmpty()) {
                        dailyCache.put(requestedDate, new CachedDaily(data.day().format(DAY_FORMATTER),
                                data.rows(), data.teams(), System.currentTimeMillis()));
                    }
                })
                .map(data -> buildDailyRes(data.day().format(DAY_FORMATTER), data.rows(), data.teams(),
                        statType, limit));
    }

    /**
     * 拉取比赛日球员数据：无完赛比赛且未指定日期时向前回溯，最多 {@link #MAX_DAILY_LOOKBACK_DAYS} 天
     */
    private Mono<DayBoxes> fetchDailyBoxes(LocalDate day, boolean explicit, int attempts) {
        return espnScoreboardClient.fetchDayBoxes(day.format(DAY_FORMATTER))
                .map(boxes -> new DayBoxes(day, boxes))
                .flatMap(data -> {
                    if (!data.boxes().isEmpty() || explicit || attempts >= MAX_DAILY_LOOKBACK_DAYS) {
                        return Mono.just(data);
                    }
                    return fetchDailyBoxes(day.minusDays(1), false, attempts + 1);
                });
    }

    /**
     * 概要数据表转每日最佳行：同名同队合并（同一球员当日只应有一场），并按库内球员补齐 ID/位置/头像
     */
    private static List<DailyRow> toDailyRows(List<EspnGameDetail> details, Map<String, LocalPlayer> index) {
        Map<String, DailyRow> merged = new LinkedHashMap<>();
        for (EspnGameDetail detail : details) {
            for (EspnTeamBox box : detail.boxes()) {
                Map<String, Integer> columns = columnIndex(box.columns());
                for (EspnPlayerLine line : box.players()) {
                    if (!line.played()) {
                        continue;
                    }
                    String team = StringUtils.trimToEmpty(box.team());
                    String key = team + "|" + normalizeName(line.name());
                    DailyRow row = merged.computeIfAbsent(key,
                            k -> newDailyRow(line.name(), team, index.get(normalizeName(line.name()))));
                    row.points += dailyStat(line, columns, "PTS");
                    row.rebounds += dailyStat(line, columns, "REB");
                    row.assists += dailyStat(line, columns, "AST");
                    row.steals += dailyStat(line, columns, "STL");
                    row.blocks += dailyStat(line, columns, "BLK");
                    addShooting(row, line, columns);
                }
            }
        }
        return new ArrayList<>(merged.values());
    }

    private static DailyRow newDailyRow(String name, String team, LocalPlayer local) {
        return new DailyRow(name, team,
                local == null ? 0L : local.playerId(),
                local == null ? "" : defaultString(local.position()),
                local == null ? "" : defaultString(local.photoUrl()));
    }

    /**
     * 概要数据表转每日最佳球队行：单场全队球员数据合计（排序在出参时按统计类型计算）
     */
    private static List<TeamDailyRow> toDailyTeams(List<EspnGameDetail> details) {
        Map<String, TeamDailyRow> merged = new LinkedHashMap<>();
        for (EspnGameDetail detail : details) {
            String[] match = detail.game().matchKey().split("\\|");
            String away = match.length > 1 ? match[1] : "";
            String home = match.length > 2 ? match[2] : "";
            for (EspnTeamBox box : detail.boxes()) {
                String team = StringUtils.trimToEmpty(box.team());
                if (team.isEmpty()) {
                    continue;
                }
                Map<String, Integer> columns = columnIndex(box.columns());
                TeamDailyRow row = merged.computeIfAbsent(team, TeamDailyRow::new);
                for (EspnPlayerLine line : box.players()) {
                    if (!line.played()) {
                        continue;
                    }
                    row.points += dailyStat(line, columns, "PTS");
                    row.rebounds += dailyStat(line, columns, "REB");
                    row.assists += dailyStat(line, columns, "AST");
                    row.steals += dailyStat(line, columns, "STL");
                    row.blocks += dailyStat(line, columns, "BLK");
                    addTeamShooting(row, line, columns);
                }
                row.margin += teamMargin(detail.game(), team, away, home);
            }
        }
        return new ArrayList<>(merged.values());
    }

    private static void addShooting(DailyRow row, EspnPlayerLine line, Map<String, Integer> columns) {
        row.threePointers += madeCount(dailyRawStat(line, columns, "3PT"));
        String fieldGoals = dailyRawStat(line, columns, "FG");
        row.fieldGoalsMade += madeCount(fieldGoals);
        row.fieldGoalsAttempted += attemptedCount(fieldGoals);
    }

    private static void addTeamShooting(TeamDailyRow row, EspnPlayerLine line, Map<String, Integer> columns) {
        row.threePointers += madeCount(dailyRawStat(line, columns, "3PT"));
        String fieldGoals = dailyRawStat(line, columns, "FG");
        row.fieldGoalsMade += madeCount(fieldGoals);
        row.fieldGoalsAttempted += attemptedCount(fieldGoals);
    }

    /**
     * 拆分「命中-出手」格式的统计值（如 FG 的 8-17），命中数用于三分/命中率统计
     */
    private static int madeCount(String raw) {
        int dash = raw.indexOf('-');
        String made = StringUtils.trimToEmpty(dash >= 0 ? raw.substring(0, dash) : raw);
        return StringUtils.isNumeric(made) ? Integer.parseInt(made) : 0;
    }

    private static int attemptedCount(String raw) {
        int dash = raw.indexOf('-');
        String attempted = StringUtils.trimToEmpty(dash >= 0 ? raw.substring(dash + 1) : "");
        return StringUtils.isNumeric(attempted) ? Integer.parseInt(attempted) : 0;
    }

    private static int teamMargin(EspnGame game, String team, String away, String home) {
        if (game.awayScore() == null || game.homeScore() == null) {
            return 0;
        }
        if (team.equals(home)) {
            return game.homeScore() - game.awayScore();
        }
        if (team.equals(away)) {
            return game.awayScore() - game.homeScore();
        }
        return 0;
    }

    private static Map<String, Integer> columnIndex(List<String> columns) {
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < columns.size(); i++) {
            index.putIfAbsent(StringUtils.trimToEmpty(columns.get(i)).toUpperCase(Locale.ROOT), i);
        }
        return index;
    }

    private static int dailyStat(EspnPlayerLine line, Map<String, Integer> columns, String label) {
        Integer index = columns.get(label);
        if (index == null || index < 0 || index >= line.stats().size()) {
            return 0;
        }
        String raw = StringUtils.trimToEmpty(line.stats().get(index));
        return StringUtils.isNumeric(raw) ? Integer.parseInt(raw) : 0;
    }

    private static String dailyRawStat(EspnPlayerLine line, Map<String, Integer> columns, String label) {
        Integer index = columns.get(label);
        if (index == null || index < 0 || index >= line.stats().size()) {
            return "";
        }
        return StringUtils.trimToEmpty(line.stats().get(index));
    }

    private static LocalDate parseDay(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value, DAY_FORMATTER);
        } catch (DateTimeParseException e) {
            log.warn("每日最佳日期格式非法: {}", value);
            return null;
        }
    }

    private static GetDailyLeadersRes buildDailyRes(String date, List<DailyRow> rows, List<TeamDailyRow> teams,
                                                    String statType, int limit) {
        List<DailyRow> playerOrder = rows.stream()
                .filter(row -> !"fieldGoalPercentage".equals(statType)
                        || row.fieldGoalsAttempted >= MIN_DAILY_FG_ATTEMPTS)
                .sorted(Comparator.comparingDouble((DailyRow row) -> dailyStatValue(statType, row)).reversed()
                        .thenComparing(row -> row.name))
                .toList();
        List<TeamDailyRow> teamOrder = teams.stream()
                .filter(row -> !"fieldGoalPercentage".equals(statType)
                        || row.fieldGoalsAttempted >= MIN_DAILY_FG_ATTEMPTS)
                .sorted(Comparator.comparingDouble((TeamDailyRow row) -> dailyTeamStatValue(statType, row)).reversed()
                        .thenComparing(row -> row.team))
                .toList();
        return GetDailyLeadersRes.newBuilder()
                .setDate(date)
                .addAllLeaders(playerOrder.stream()
                        .limit(limit)
                        .map(row -> DailyLeaderInfo.newBuilder()
                                .setPlayerId(row.playerId)
                                .setName(row.name)
                                .setTeam(row.team)
                                .setPosition(row.position)
                                .setPoints(row.points)
                                .setRebounds(row.rebounds)
                                .setAssists(row.assists)
                                .setSteals(row.steals)
                                .setBlocks(row.blocks)
                                .setThreePointers(row.threePointers)
                                .setFieldGoalPercentage(shootingPercentage(row.fieldGoalsMade,
                                        row.fieldGoalsAttempted))
                                .setPhotoUrl(row.photoUrl)
                                .build())
                        .toList())
                .addAllTeams(teamOrder.stream()
                        .limit(TEAM_LEADER_LIMIT)
                        .map(row -> DailyTeamLeaderInfo.newBuilder()
                                .setTeam(row.team)
                                .setPoints(row.points)
                                .setRebounds(row.rebounds)
                                .setAssists(row.assists)
                                .setSteals(row.steals)
                                .setBlocks(row.blocks)
                                .setThreePointers(row.threePointers)
                                .setFieldGoalPercentage(shootingPercentage(row.fieldGoalsMade,
                                        row.fieldGoalsAttempted))
                                .setMargin(row.margin)
                                .build())
                        .toList())
                .build();
    }

    /**
     * 单日球员数据取值：命中率由当日命中/出手自算，其余为单场累计值
     */
    private static double dailyStatValue(String statType, DailyRow row) {
        return switch (statType) {
            case "rebounds" -> row.rebounds;
            case "assists" -> row.assists;
            case "steals" -> row.steals;
            case "blocks" -> row.blocks;
            case "threePointers" -> row.threePointers;
            case "fieldGoalPercentage" -> shootingPercentage(row.fieldGoalsMade, row.fieldGoalsAttempted);
            default -> row.points;
        };
    }

    /**
     * 单日球队数据取值：全队合计（命中率同样按合计命中/出手自算）
     */
    private static double dailyTeamStatValue(String statType, TeamDailyRow row) {
        return switch (statType) {
            case "rebounds" -> row.rebounds;
            case "assists" -> row.assists;
            case "steals" -> row.steals;
            case "blocks" -> row.blocks;
            case "threePointers" -> row.threePointers;
            case "fieldGoalPercentage" -> shootingPercentage(row.fieldGoalsMade, row.fieldGoalsAttempted);
            default -> row.points;
        };
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
        CachedStandings cached = standingsCache.get();
        if (cached != null && cached.requestedSeason() == requested
                && System.currentTimeMillis() - cached.time() < CACHE_MILLIS) {
            return Mono.just(cached.data());
        }
        return loadTeams()
                .flatMap(teams -> espnRankingClient.fetchStandings(requested)
                        .flatMap(rows -> resolveStandings(rows, requested, teams)))
                .doOnNext(data -> {
                    if (!data.rows().isEmpty()) {
                        standingsCache.set(new CachedStandings(requested, data, System.currentTimeMillis()));
                    }
                });
    }

    /**
     * 当前赛季尚无比赛数据时回落到上一赛季（新赛季开赛前 ESPN 全为 0 胜 0 负）
     */
    private Mono<StandingsData> resolveStandings(List<EspnTeamStanding> rows, int requested,
                                                 Map<String, Team> teams) {
        if (hasPlayedGames(rows) || requested <= 1) {
            return Mono.just(new StandingsData(requested, toStandingRows(rows, teams)));
        }
        log.info("球队战绩赛季 {} 暂无比赛数据，回落到赛季 {}", requested, requested - 1);
        return espnRankingClient.fetchStandings(requested - 1)
                .map(prev -> new StandingsData(requested - 1, toStandingRows(prev, teams)));
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
        CachedLeaders cached = leadersCache.get();
        if (cached != null && cached.requestedSeason() == requested
                && System.currentTimeMillis() - cached.time() < CACHE_MILLIS) {
            return Mono.just(cached.data());
        }
        return loadPlayerIndex()
                .flatMap(index -> espnRankingClient.fetchPlayerStats(requested)
                        .flatMap(rows -> resolveLeaders(rows, requested, index)))
                .doOnNext(data -> {
                    if (!data.rows().isEmpty()) {
                        leadersCache.set(new CachedLeaders(requested, data, System.currentTimeMillis()));
                    }
                });
    }

    private Mono<LeadersData> resolveLeaders(List<EspnPlayerStat> rows, int requested,
                                             Map<String, LocalPlayer> index) {
        if (hasStatsGames(rows) || requested <= 1) {
            return Mono.just(new LeadersData(requested, toPlayerStatRows(rows, index)));
        }
        log.info("球员统计赛季 {} 暂无出场数据，回落到赛季 {}", requested, requested - 1);
        return espnRankingClient.fetchPlayerStats(requested - 1)
                .map(prev -> new LeadersData(requested - 1, toPlayerStatRows(prev, index)));
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

    /**
     * 整季替换：先清理该赛季旧行再写入，避免数据源切换后旧球员 ID 残留
     */
    private Mono<Void> saveStandings(int season, List<StandingRow> rows) {
        if (rows.isEmpty()) {
            return Mono.empty();
        }
        return teamStandingsRepository.deleteBySeason(season)
                .thenMany(Flux.fromIterable(rows)
                        .concatMap(row -> teamStandingsRepository.upsert(toStandingEntity(season, row))))
                .then();
    }

    /**
     * 整季替换球员统计后重算该赛季的联盟对比（对比行随统计一起预计算落库）
     */
    private Mono<Void> savePlayerStats(int season, List<PlayerStatRow> rows) {
        if (rows.isEmpty()) {
            return Mono.empty();
        }
        return playerSeasonStatsRepository.deleteBySeason(season)
                .thenMany(Flux.fromIterable(rows)
                        .concatMap(row -> playerSeasonStatsRepository.upsert(toPlayerStatEntity(season, row))))
                .then(leagueCompareService.rebuildSeason(season));
    }

    /**
     * 库内球队主数据（ESPN 只提供战绩，城市/队名/东西部/赛区以库内 team 表为准）
     */
    private Mono<Map<String, Team>> loadTeams() {
        return teamRepository.findAll()
                .filter(team -> defaultInt(team.getIsDeleted()) == 0 && StringUtils.isNotBlank(team.getKey()))
                .collectList()
                .map(teams -> teams.stream()
                        .collect(Collectors.toMap(Team::getKey, Function.identity(), (first, second) -> first)));
    }

    /**
     * 库内球员索引：姓名归一后映射到 player_id，优先保留有 NBA 官方头像的记录
     * （同一球员可能因历史同步存在多条记录）
     */
    private Mono<Map<String, LocalPlayer>> loadPlayerIndex() {
        return playerRepository.findAllActive()
                .collectList()
                .map(players -> {
                    Map<String, LocalPlayer> index = new HashMap<>();
                    Map<String, LocalPlayer> shortNames = new HashMap<>();
                    for (TimerPlayer player : players) {
                        LocalPlayer local = new LocalPlayer(defaultInt(player.getPlayerId()),
                                defaultString(player.getPosition()), defaultString(player.getPhotoUrl()));
                        putPreferred(index, normalizeName(player.getFirstName() + " " + player.getLastName()), local);
                        putPreferred(index, normalizeName(player.getDraftKingsName()), local);
                        // 全名不一致时（如 Alex/Alexandre Sarr）用「姓+队+名首字母」兜底，重名则放弃
                        String shortKey = shortNameKey(player.getFirstName(), player.getLastName(), player.getTeam());
                        if (StringUtils.isNotBlank(shortKey)) {
                            if (shortNames.containsKey(shortKey)) {
                                shortNames.put(shortKey, null);
                            } else {
                                shortNames.put(shortKey, local);
                            }
                        }
                    }
                    shortNames.forEach((key, local) -> {
                        if (local != null) {
                            index.putIfAbsent(key, local);
                        }
                    });
                    return index;
                });
    }

    private static void putPreferred(Map<String, LocalPlayer> index, String key, LocalPlayer candidate) {
        if (StringUtils.isBlank(key)) {
            return;
        }
        LocalPlayer existing = index.get(key);
        if (existing == null || (!hasNbaPhoto(existing.photoUrl()) && hasNbaPhoto(candidate.photoUrl()))) {
            index.put(key, candidate);
        }
    }

    private static boolean hasNbaPhoto(String photoUrl) {
        return Strings.CS.startsWith(photoUrl, "https://cdn.nba.com");
    }

    /**
     * 姓名归一：去重音/标点/大小写与 Jr./Sr./II 等后缀（ESPN 与库内写法差异）
     */
    private static String normalizeName(String name) {
        String value = StringUtils.trimToEmpty(name);
        if (value.isEmpty()) {
            return "";
        }
        String stripped = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return stripped.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]", "")
                .replaceFirst("(jr|sr|ii|iii|iv)$", "");
    }

    /**
     * 「姓+球队+名首字母」匹配键，仅用于全名匹配失败时的唯一兜底
     */
    private static String shortNameKey(String firstName, String lastName, String team) {
        String first = normalizeName(firstName);
        String last = normalizeName(lastName);
        if (first.isEmpty() || last.isEmpty()) {
            return "";
        }
        return last + "|" + StringUtils.trimToEmpty(team) + "|" + first.charAt(0);
    }

    private static List<StandingRow> toStandingRows(List<EspnTeamStanding> rows, Map<String, Team> teams) {
        List<StandingRow> result = new ArrayList<>(rows.size());
        for (EspnTeamStanding row : rows) {
            Team team = teams.get(row.teamKey());
            if (team == null) {
                log.warn("ESPN 球队未匹配库内主数据: {}", row.teamKey());
                continue;
            }
            StandingRow standing = new StandingRow();
            standing.teamId = defaultInt(team.getTeamId());
            standing.key = defaultString(team.getKey());
            standing.city = defaultString(team.getCity());
            standing.name = defaultString(team.getName());
            standing.conference = defaultString(team.getConference());
            standing.division = defaultString(team.getDivision());
            standing.wins = row.wins();
            standing.losses = row.losses();
            standing.winPct = winPctOf(row.wins(), row.losses());
            standing.gamesBack = row.gamesBehind();
            standing.lastTenWins = row.lastTenWins();
            standing.lastTenLosses = row.lastTenLosses();
            standing.streakDescription = row.streak();
            standing.pointsPerGameFor = row.pointsPerGameFor();
            standing.pointsPerGameAgainst = row.pointsPerGameAgainst();
            result.add(standing);
        }
        return result;
    }

    private static List<PlayerStatRow> toPlayerStatRows(List<EspnPlayerStat> rows,
                                                        Map<String, LocalPlayer> index) {
        List<PlayerStatRow> result = new ArrayList<>(rows.size());
        for (EspnPlayerStat row : rows) {
            LocalPlayer local = resolveLocalPlayer(index, row);
            long playerId = local == null ? row.athlete().espnId() : local.playerId();
            String position = local != null && StringUtils.isNotBlank(local.position())
                    ? local.position() : row.position();
            result.add(new PlayerStatRow(playerId, row.athlete().name(), row.team(), position, row.games(),
                    row.points(), row.rebounds(), row.assists(), row.steals(), row.blocks(),
                    row.threePointersMade(), row.fieldGoalsMade(), row.fieldGoalsAttempted()));
        }
        return result;
    }

    private static LocalPlayer resolveLocalPlayer(Map<String, LocalPlayer> index, EspnPlayerStat row) {
        LocalPlayer local = index.get(normalizeName(row.athlete().name()));
        if (local != null) {
            return local;
        }
        return index.get(shortNameKey(row.athlete().firstName(), row.athlete().lastName(), row.team()));
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
     * 胜率自算（避免上游对 0 场次返回异常值），保留三位小数
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
                        .addAllTeams(rankTeamLeaders(data.rows(), statType, TEAM_LEADER_LIMIT))
                        .build());
    }

    /**
     * 赛季球队榜：按球队汇总球员赛季总计，场次取队内最高出场数，场均 = 全队总计 / 场次
     */
    private static List<TeamStatLeaderInfo> rankTeamLeaders(List<PlayerStatRow> rows, String statType, int limit) {
        Map<String, TeamAggregate> aggregates = new LinkedHashMap<>();
        for (PlayerStatRow row : rows) {
            if (StringUtils.isBlank(row.team()) || row.games() <= 0) {
                continue;
            }
            TeamAggregate agg = aggregates.computeIfAbsent(row.team(), TeamAggregate::new);
            agg.games = Math.max(agg.games, row.games());
            agg.points += row.points();
            agg.rebounds += row.rebounds();
            agg.assists += row.assists();
            agg.steals += row.steals();
            agg.blockedShots += row.blockedShots();
            agg.threePointersMade += row.threePointersMade();
            agg.fieldGoalsMade += row.fieldGoalsMade();
            agg.fieldGoalsAttempted += row.fieldGoalsAttempted();
        }
        return aggregates.values().stream()
                .filter(agg -> agg.games > 0)
                .map(agg -> new TeamLeaderCandidate(agg, teamStatValue(statType, agg)))
                .sorted(Comparator.comparingDouble(TeamLeaderCandidate::value).reversed()
                        .thenComparing(candidate -> candidate.aggregate().team))
                .limit(limit)
                .map(candidate -> TeamStatLeaderInfo.newBuilder()
                        .setTeam(candidate.aggregate().team)
                        .setGames(candidate.aggregate().games)
                        .setValue(candidate.value())
                        .build())
                .toList();
    }

    private static double teamStatValue(String statType, TeamAggregate team) {
        return switch (statType) {
            case "points" -> perGame(team.points, team.games);
            case "rebounds" -> perGame(team.rebounds, team.games);
            case "assists" -> perGame(team.assists, team.games);
            case "steals" -> perGame(team.steals, team.games);
            case "blocks" -> perGame(team.blockedShots, team.games);
            case "threePointers" -> perGame(team.threePointersMade, team.games);
            case "fieldGoalPercentage" -> shootingPercentage(team.fieldGoalsMade, team.fieldGoalsAttempted);
            default -> 0;
        };
    }

    /**
     * 按统计类型对全量球员统计排序取前 limit（出场场次不足者过滤；命中率另设出手门槛；赛季初场次很少时不做场次门槛过滤）
     */
    private static List<PlayerStatLeaderInfo> rankLeaders(List<PlayerStatRow> rows, String statType, int limit) {
        if (rows.isEmpty()) {
            return List.of();
        }
        int maxGames = rows.stream().mapToInt(PlayerStatRow::games).max().orElse(0);
        int minGames = maxGames <= MIN_QUALIFY_GAMES
                ? 1
                : Math.max(MIN_QUALIFY_GAMES, (int) Math.ceil(maxGames * QUALIFY_GAMES_RATIO));
        return rows.stream()
                .filter(row -> row.games() >= minGames)
                .filter(row -> !"fieldGoalPercentage".equals(statType)
                        || row.fieldGoalsAttempted() >= MIN_SEASON_FG_ATTEMPTS)
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

    private static double perGame(Double total, int games) {
        return roundOneDecimal(perGameRaw(total, games));
    }

    /**
     * 未取整的场均值：名次与上限比较用原始值，避免一位小数取整后并列
     */
    private static double perGameRaw(Double total, int games) {
        return games <= 0 || total == null ? 0.0 : total / games;
    }

    private static double roundOneDecimal(double value) {
        return Math.round(value * 10) / 10.0;
    }

    /**
     * 命中率由命中/出手自算（ESPN 的 FG% 为四舍五入值），保留一位小数
     */
    private static double fieldGoalPercentage(PlayerStatRow player) {
        return shootingPercentage(player.fieldGoalsMade(), player.fieldGoalsAttempted());
    }

    private static double shootingPercentage(double made, double attempts) {
        if (attempts <= 0) {
            return 0;
        }
        return Math.round(made / attempts * 1000) / 10.0;
    }

    private static boolean hasPlayedGames(List<EspnTeamStanding> rows) {
        return rows.stream().anyMatch(row -> row.wins() + row.losses() > 0);
    }

    private static boolean hasStatsGames(List<EspnPlayerStat> rows) {
        return rows.stream().anyMatch(row -> row.games() > 0);
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
     * 球员统计统一中间行：库内实体与 ESPN 数据均映射为此结构后计算榜单
     */
    private record PlayerStatRow(long playerId, String name, String team, String position, int games,
                                 double points, double rebounds, double assists, double steals,
                                 double blockedShots, double threePointersMade, double fieldGoalsMade,
                                 double fieldGoalsAttempted) {
    }

    /**
     * 库内球员映射结果（photoUrl 仅用于优先选取有 NBA 官方头像的记录）
     */
    private record LocalPlayer(long playerId, String position, String photoUrl) {
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

    private record CachedStandings(int requestedSeason, StandingsData data, long time) {
    }

    private record CachedLeaders(int requestedSeason, LeadersData data, long time) {
    }

    /**
     * 每日最佳可变聚合行：球员身份与库内映射固定，单场数据累加
     */
    private static final class DailyRow {

        private final String name;

        private final String team;

        private final long playerId;

        private final String position;

        private final String photoUrl;

        private int points;

        private int rebounds;

        private int assists;

        private int steals;

        private int blocks;

        private int threePointers;

        private int fieldGoalsMade;

        private int fieldGoalsAttempted;

        private DailyRow(String name, String team, long playerId, String position, String photoUrl) {
            this.name = name;
            this.team = team;
            this.playerId = playerId;
            this.position = position;
            this.photoUrl = photoUrl;
        }
    }

    private record DayBoxes(LocalDate day, List<EspnGameDetail> boxes) {
    }

    /**
     * 每日最佳球队可变聚合行：当日全队数据累加，margin 为净胜分
     */
    private static final class TeamDailyRow {

        private final String team;

        private int points;

        private int rebounds;

        private int assists;

        private int steals;

        private int blocks;

        private int threePointers;

        private int fieldGoalsMade;

        private int fieldGoalsAttempted;

        private int margin;

        private TeamDailyRow(String team) {
            this.team = team;
        }
    }

    /**
     * 赛季球队榜聚合：场次取队内最高出场数，各项为球员赛季总计之和
     */
    private static final class TeamAggregate {

        private final String team;

        private int games;

        private double points;

        private double rebounds;

        private double assists;

        private double steals;

        private double blockedShots;

        private double threePointersMade;

        private double fieldGoalsMade;

        private double fieldGoalsAttempted;

        private TeamAggregate(String team) {
            this.team = team;
        }
    }

    private record TeamLeaderCandidate(TeamAggregate aggregate, double value) {
    }

    private record DailyData(LocalDate day, List<DailyRow> rows, List<TeamDailyRow> teams) {
    }

    private record CachedDaily(String date, List<DailyRow> rows, List<TeamDailyRow> teams, long time) {
    }
}
