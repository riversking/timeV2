package com.rivers.nba.service.impl;

import com.rivers.nba.client.EspnRankingClient;
import com.rivers.nba.client.EspnRankingClient.EspnPlayerStat;
import com.rivers.nba.client.EspnScoreboardClient;
import com.rivers.nba.client.EspnScoreboardClient.EspnGameDetail;
import com.rivers.nba.client.EspnScoreboardClient.EspnPlayerLine;
import com.rivers.nba.client.EspnScoreboardClient.EspnTeamBox;
import com.rivers.nba.entity.PlayerSeasonHistory;
import com.rivers.nba.entity.TimerPlayer;
import com.rivers.nba.repository.PlayerRepository;
import com.rivers.nba.repository.PlayerSeasonHistoryRepository;
import com.rivers.nba.service.IPlayerSeasonHistoryService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import static com.rivers.core.util.ValueUtils.defaultDouble;
import static com.rivers.core.util.ValueUtils.defaultInt;
import static com.rivers.core.util.ValueUtils.defaultString;

/**
 * <p>
 * 球员历年数据服务实现：常规赛/季后赛取 ESPN byathlete（按赛季 + 赛程分类整季替换），
 * 季前赛 byathlete 不提供数据，按季前赛月份计分板 + 单场概要聚合球员总量。
 * 历史赛季仅在库内缺失时回填，当季由定时任务刷新；详情接口只读库。
 * </p>
 *
 * @author rivers
 * @since 2026-11-01
 */
@Slf4j
@Service
public class PlayerSeasonHistoryServiceImpl implements IPlayerSeasonHistoryService {

    /**
     * 回填赛季数：覆盖库内现役球员的完整生涯（最早 2003 赛季，如詹姆斯新秀年 2003-04）
     */
    private static final int SEASON_HISTORY_RANGE = 25;

    private static final int SEASON_TYPE_PRESEASON = 1;

    private static final int SEASON_TYPE_REGULAR = 2;

    private static final int SEASON_TYPE_PLAYOFFS = 3;

    /**
     * 季前赛窗口：赛季开始年份的 9、10 月
     */
    private static final List<String> PRESEASON_MONTHS = List.of("09", "10");

    /**
     * 赛季切换月份（7 月后进入新赛季，与排行榜口径一致）
     */
    private static final int SEASON_SWITCH_MONTH = 7;

    private static final int BATCH_SIZE = 300;

    private static final String INSERT_PREFIX = """
            INSERT INTO player_season_history (season, season_type, player_id, player_name, team, games, minutes,
            points, field_goals_made, field_goals_attempted, three_pointers_made, three_pointers_attempted,
            free_throws_made, free_throws_attempted, rebounds, assists, steals, blocked_shots, fouls,
            create_user, update_user, is_deleted)
            VALUES """;

    private static final String INSERT_VALUES =
            "(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'system', 'system', 0)";

    private static final String INSERT_SUFFIX = """
             AS new ON DUPLICATE KEY UPDATE player_name = new.player_name, team = new.team,
            games = new.games, minutes = new.minutes, points = new.points,
            field_goals_made = new.field_goals_made, field_goals_attempted = new.field_goals_attempted,
            three_pointers_made = new.three_pointers_made,
            three_pointers_attempted = new.three_pointers_attempted,
            free_throws_made = new.free_throws_made, free_throws_attempted = new.free_throws_attempted,
            rebounds = new.rebounds, assists = new.assists, steals = new.steals,
            blocked_shots = new.blocked_shots, fouls = new.fouls,
            update_user = 'system', is_deleted = 0
            """;

    private final PlayerSeasonHistoryRepository playerSeasonHistoryRepository;

    private final PlayerRepository playerRepository;

    private final EspnRankingClient espnRankingClient;

    private final EspnScoreboardClient espnScoreboardClient;

    private final DatabaseClient databaseClient;

    public PlayerSeasonHistoryServiceImpl(PlayerSeasonHistoryRepository playerSeasonHistoryRepository,
                                          PlayerRepository playerRepository,
                                          EspnRankingClient espnRankingClient,
                                          EspnScoreboardClient espnScoreboardClient,
                                          DatabaseClient databaseClient) {
        this.playerSeasonHistoryRepository = playerSeasonHistoryRepository;
        this.playerRepository = playerRepository;
        this.espnRankingClient = espnRankingClient;
        this.espnScoreboardClient = espnScoreboardClient;
        this.databaseClient = databaseClient;
    }

    @Override
    public Flux<PlayerSeasonHistory> findByPlayerId(long playerId) {
        return playerSeasonHistoryRepository.findByPlayerId(playerId);
    }

    /**
     * 以历史赛季常规赛数据作为初始化完成依据：当季由每日任务刷新，不计入判断；
     * 任一赛季缺失即视为未完成，下一个调度周期重试回填
     */
    @Override
    public Mono<Boolean> isHistoryInitialized() {
        int current = resolveSeason();
        int start = Math.max(1, current - SEASON_HISTORY_RANGE + 1);
        int seasons = Math.max(0, current - start);
        if (seasons == 0) {
            return Mono.just(true);
        }
        return Flux.range(1, seasons)
                .concatMap(offset -> playerSeasonHistoryRepository.countBySeasonType(current - offset,
                        SEASON_TYPE_REGULAR))
                .all(count -> count > 0);
    }

    /**
     * 由近及远逐季补齐三类数据（常规赛/季后赛优先，季前赛聚合最慢放最后），
     * 已存在（行数 > 0）则跳过
     */
    @Override
    public Mono<Void> backfillMissing() {
        int current = resolveSeason();
        int start = Math.max(1, current - SEASON_HISTORY_RANGE + 1);
        int seasons = Math.max(0, current - start + 1);
        return loadPlayerIndex()
                .flatMap(index -> Flux.range(0, seasons)
                        .concatMap(offset -> backfillSeason(current - offset, index))
                        .then())
                .doOnError(e -> log.warn("球员历年数据回填失败: {}", e.getMessage()))
                .onErrorResume(e -> Mono.empty());
    }

    @Override
    public Mono<Void> syncRecent() {
        int current = resolveSeason();
        return loadPlayerIndex()
                .flatMap(index -> refreshSeasonType(current, SEASON_TYPE_REGULAR, index)
                        .then(refreshSeasonType(current, SEASON_TYPE_PLAYOFFS, index))
                        .then(refreshPreseasonIfNeeded(current, index)))
                .doOnError(e -> log.warn("球员历年数据同步失败: {}", e.getMessage()))
                .onErrorResume(e -> Mono.empty());
    }

    private Mono<Void> backfillSeason(int season, Map<String, PlayerRef> index) {
        return Flux.just(SEASON_TYPE_REGULAR, SEASON_TYPE_PLAYOFFS, SEASON_TYPE_PRESEASON)
                .concatMap(seasonType -> playerSeasonHistoryRepository.countBySeasonType(season, seasonType)
                        .flatMap(stored -> stored > 0 ? Mono.empty() : rebuild(season, seasonType, index)))
                .then();
    }

    /**
     * 当季刷新：上游无数据（如季后赛未开赛）时保留库内旧数据
     */
    private Mono<Void> refreshSeasonType(int season, int seasonType, Map<String, PlayerRef> index) {
        return fetchFromUpstream(season, seasonType, index)
                .flatMap(rows -> rows.isEmpty() ? Mono.empty() : replace(season, seasonType, rows));
    }

    /**
     * 季前赛仅 9-10 月期间随任务刷新，窗口外只在库内缺失时补算一次
     */
    private Mono<Void> refreshPreseasonIfNeeded(int season, Map<String, PlayerRef> index) {
        LocalDate now = LocalDate.now();
        boolean inWindow = now.getYear() == season - 1 && now.getMonthValue() >= 9 && now.getMonthValue() <= 10;
        if (inWindow) {
            return refreshSeasonType(season, SEASON_TYPE_PRESEASON, index);
        }
        return playerSeasonHistoryRepository.countBySeasonType(season, SEASON_TYPE_PRESEASON)
                .flatMap(stored -> stored > 0 ? Mono.empty()
                        : refreshSeasonType(season, SEASON_TYPE_PRESEASON, index));
    }

    private Mono<Void> rebuild(int season, int seasonType, Map<String, PlayerRef> index) {
        return fetchFromUpstream(season, seasonType, index)
                .flatMap(rows -> replace(season, seasonType, rows));
    }

    private Mono<List<PlayerSeasonHistory>> fetchFromUpstream(int season, int seasonType,
                                                              Map<String, PlayerRef> index) {
        if (seasonType == SEASON_TYPE_PRESEASON) {
            return fetchPreseason(season, index);
        }
        return espnRankingClient.fetchPlayerStats(season, seasonType)
                .map(stats -> stats.stream()
                        .map(stat -> toHistory(season, seasonType, stat, index))
                        .filter(Objects::nonNull)
                        .toList());
    }

    /**
     * 季前赛：按赛季前一年的 9、10 月计分板逐场取概要，聚合成球员赛季总量
     */
    private Mono<List<PlayerSeasonHistory>> fetchPreseason(int season, Map<String, PlayerRef> index) {
        int year = season - 1;
        List<String> months = PRESEASON_MONTHS.stream().map(month -> year + month).toList();
        return Flux.fromIterable(months)
                .concatMap(month -> espnScoreboardClient.fetchMonthBoxes(month, SEASON_TYPE_PRESEASON))
                .collectList()
                .map(days -> aggregateBoxScores(season, days, index));
    }

    private static PlayerSeasonHistory toHistory(int season, int seasonType, EspnPlayerStat stat,
                                                 Map<String, PlayerRef> index) {
        PlayerRef local = resolveLocalPlayer(index, stat);
        if (local == null) {
            return null;
        }
        PlayerSeasonHistory row = new PlayerSeasonHistory();
        row.setSeason(season);
        row.setSeasonType(seasonType);
        row.setPlayerId(local.playerId());
        row.setPlayerName(stat.athlete().name());
        row.setTeam(StringUtils.defaultIfBlank(stat.team(), local.team()));
        row.setGames(stat.games());
        row.setMinutes(stat.minutes());
        row.setPoints(stat.points());
        row.setFieldGoalsMade(stat.fieldGoalsMade());
        row.setFieldGoalsAttempted(stat.fieldGoalsAttempted());
        row.setThreePointersMade(stat.threePointersMade());
        row.setThreePointersAttempted(stat.threePointersAttempted());
        row.setFreeThrowsMade(stat.freeThrowsMade());
        row.setFreeThrowsAttempted(stat.freeThrowsAttempted());
        row.setRebounds(stat.rebounds());
        row.setAssists(stat.assists());
        row.setSteals(stat.steals());
        row.setBlockedShots(stat.blocks());
        row.setFouls(stat.fouls());
        return row;
    }

    private static List<PlayerSeasonHistory> aggregateBoxScores(int season, List<List<EspnGameDetail>> days,
                                                                Map<String, PlayerRef> index) {
        Map<Long, PlayerSeasonHistory> totals = new LinkedHashMap<>();
        for (List<EspnGameDetail> day : days) {
            for (EspnGameDetail detail : day) {
                for (EspnTeamBox box : detail.boxes()) {
                    Map<String, Integer> columns = indexColumns(box.columns());
                    for (EspnPlayerLine line : box.players()) {
                        if (!line.played()) {
                            continue;
                        }
                        PlayerRef local = resolveLocalPlayer(index, line.name(), box.team());
                        if (local == null) {
                            continue;
                        }
                        PlayerSeasonHistory row = totals.computeIfAbsent(local.playerId(),
                                ignored -> newHistoryRow(season, local.playerId(), line.name(), box.team()));
                        accumulate(row, line.stats(), columns);
                    }
                }
            }
        }
        return new ArrayList<>(totals.values());
    }

    private static PlayerSeasonHistory newHistoryRow(int season, long playerId, String name, String team) {
        PlayerSeasonHistory row = new PlayerSeasonHistory();
        row.setSeason(season);
        row.setSeasonType(SEASON_TYPE_PRESEASON);
        row.setPlayerId(playerId);
        row.setPlayerName(name);
        row.setTeam(team);
        row.setGames(0);
        row.setMinutes(0.0);
        row.setPoints(0.0);
        row.setFieldGoalsMade(0.0);
        row.setFieldGoalsAttempted(0.0);
        row.setThreePointersMade(0.0);
        row.setThreePointersAttempted(0.0);
        row.setFreeThrowsMade(0.0);
        row.setFreeThrowsAttempted(0.0);
        row.setRebounds(0.0);
        row.setAssists(0.0);
        row.setSteals(0.0);
        row.setBlockedShots(0.0);
        row.setFouls(0.0);
        return row;
    }

    /**
     * 单场数据累加到赛季总量（表头按列名取值，兼容不同赛季的列顺序）
     */
    private static void accumulate(PlayerSeasonHistory row, List<String> stats, Map<String, Integer> columns) {
        row.setGames(defaultInt(row.getGames()) + 1);
        row.setMinutes(defaultDouble(row.getMinutes()) + parseMinutes(statValue(stats, columns, "MIN")));
        row.setPoints(defaultDouble(row.getPoints()) + parseDouble(statValue(stats, columns, "PTS")));
        double[] fieldGoals = parseMadeAttempt(statValue(stats, columns, "FG"));
        row.setFieldGoalsMade(defaultDouble(row.getFieldGoalsMade()) + fieldGoals[0]);
        row.setFieldGoalsAttempted(defaultDouble(row.getFieldGoalsAttempted()) + fieldGoals[1]);
        double[] threePointers = parseMadeAttempt(statValue(stats, columns, "3PT"));
        row.setThreePointersMade(defaultDouble(row.getThreePointersMade()) + threePointers[0]);
        row.setThreePointersAttempted(defaultDouble(row.getThreePointersAttempted()) + threePointers[1]);
        double[] freeThrows = parseMadeAttempt(statValue(stats, columns, "FT"));
        row.setFreeThrowsMade(defaultDouble(row.getFreeThrowsMade()) + freeThrows[0]);
        row.setFreeThrowsAttempted(defaultDouble(row.getFreeThrowsAttempted()) + freeThrows[1]);
        row.setRebounds(defaultDouble(row.getRebounds()) + parseDouble(statValue(stats, columns, "REB")));
        row.setAssists(defaultDouble(row.getAssists()) + parseDouble(statValue(stats, columns, "AST")));
        row.setSteals(defaultDouble(row.getSteals()) + parseDouble(statValue(stats, columns, "STL")));
        row.setBlockedShots(defaultDouble(row.getBlockedShots()) + parseDouble(statValue(stats, columns, "BLK")));
        row.setFouls(defaultDouble(row.getFouls()) + parseDouble(statValue(stats, columns, "PF")));
    }

    private static Map<String, Integer> indexColumns(List<String> columns) {
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < columns.size(); i++) {
            index.put(columns.get(i), i);
        }
        return index;
    }

    private static String statValue(List<String> stats, Map<String, Integer> columns, String label) {
        Integer index = columns.get(label);
        if (index == null || index < 0 || index >= stats.size()) {
            return "";
        }
        return StringUtils.trimToEmpty(stats.get(index));
    }

    /**
     * 比赛时长（"18" 或 "18:23"）转分钟
     */
    private static double parseMinutes(String value) {
        int colon = value.indexOf(':');
        if (colon > 0) {
            return parseDouble(value.substring(0, colon)) + parseDouble(value.substring(colon + 1)) / 60.0;
        }
        return parseDouble(value);
    }

    /**
     * "命中-出手" 格式转数组，缺失或 "-" 按 0 处理
     */
    private static double[] parseMadeAttempt(String value) {
        int dash = value.indexOf('-');
        if (dash <= 0) {
            return new double[]{0, 0};
        }
        return new double[]{parseDouble(value.substring(0, dash)), parseDouble(value.substring(dash + 1))};
    }

    private static double parseDouble(String value) {
        try {
            return Double.parseDouble(StringUtils.trimToEmpty(value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 整季整类替换：先清理再批量写入，避免上游剔除的球员残留
     */
    private Mono<Void> replace(int season, int seasonType, List<PlayerSeasonHistory> rows) {
        if (rows.isEmpty()) {
            return Mono.empty();
        }
        return playerSeasonHistoryRepository.deleteBySeasonType(season, seasonType)
                .then(insertAll(rows))
                .doOnSuccess(ignored -> log.info("球员历年数据已写入: season={}, seasonType={}, rows={}",
                        season, seasonType, rows.size()))
                .doOnError(e -> log.warn("球员历年数据写入失败: season={}, seasonType={}, error={}",
                        season, seasonType, e.getMessage()))
                .onErrorResume(e -> Mono.empty());
    }

    private Mono<Void> insertAll(List<PlayerSeasonHistory> rows) {
        return Flux.fromIterable(partition(rows))
                .concatMap(this::insertBatch)
                .then();
    }

    /**
     * 批量 upsert（按 season + season_type + player_id 唯一键），分片控制单条 SQL 参数数量
     */
    private Mono<Void> insertBatch(List<PlayerSeasonHistory> rows) {
        String placeholders = String.join(", ", Collections.nCopies(rows.size(), INSERT_VALUES));
        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(INSERT_PREFIX + placeholders + INSERT_SUFFIX);
        int index = 0;
        for (PlayerSeasonHistory row : rows) {
            spec = spec.bind(index++, row.getSeason())
                    .bind(index++, row.getSeasonType())
                    .bind(index++, row.getPlayerId())
                    .bind(index++, defaultString(row.getPlayerName()))
                    .bind(index++, defaultString(row.getTeam()))
                    .bind(index++, defaultInt(row.getGames()))
                    .bind(index++, defaultDouble(row.getMinutes()))
                    .bind(index++, defaultDouble(row.getPoints()))
                    .bind(index++, defaultDouble(row.getFieldGoalsMade()))
                    .bind(index++, defaultDouble(row.getFieldGoalsAttempted()))
                    .bind(index++, defaultDouble(row.getThreePointersMade()))
                    .bind(index++, defaultDouble(row.getThreePointersAttempted()))
                    .bind(index++, defaultDouble(row.getFreeThrowsMade()))
                    .bind(index++, defaultDouble(row.getFreeThrowsAttempted()))
                    .bind(index++, defaultDouble(row.getRebounds()))
                    .bind(index++, defaultDouble(row.getAssists()))
                    .bind(index++, defaultDouble(row.getSteals()))
                    .bind(index++, defaultDouble(row.getBlockedShots()))
                    .bind(index++, defaultDouble(row.getFouls()));
        }
        return spec.fetch().rowsUpdated().then();
    }

    private static List<List<PlayerSeasonHistory>> partition(List<PlayerSeasonHistory> rows) {
        List<List<PlayerSeasonHistory>> parts = new ArrayList<>();
        for (int i = 0; i < rows.size(); i += BATCH_SIZE) {
            parts.add(rows.subList(i, Math.min(rows.size(), i + BATCH_SIZE)));
        }
        return parts;
    }

    private Mono<Map<String, PlayerRef>> loadPlayerIndex() {
        return playerRepository.findAllActive()
                .collectList()
                .map(players -> {
                    Map<String, PlayerRef> index = new HashMap<>();
                    Map<String, PlayerRef> shortNames = new HashMap<>();
                    for (TimerPlayer player : players) {
                        PlayerRef ref = new PlayerRef(defaultInt(player.getPlayerId()),
                                defaultString(player.getTeam()), defaultString(player.getPhotoUrl()));
                        putPreferred(index, normalizeName(player.getFirstName() + " " + player.getLastName()), ref);
                        putPreferred(index, normalizeName(player.getDraftKingsName()), ref);
                        String shortKey = shortNameKey(player.getFirstName(), player.getLastName(), player.getTeam());
                        if (StringUtils.isNotBlank(shortKey)) {
                            if (shortNames.containsKey(shortKey)) {
                                shortNames.put(shortKey, null);
                            } else {
                                shortNames.put(shortKey, ref);
                            }
                        }
                    }
                    shortNames.forEach((key, ref) -> {
                        if (ref != null) {
                            index.putIfAbsent(key, ref);
                        }
                    });
                    return index;
                });
    }

    private static PlayerRef resolveLocalPlayer(Map<String, PlayerRef> index, EspnPlayerStat stat) {
        PlayerRef local = index.get(normalizeName(stat.athlete().name()));
        if (local != null) {
            return local;
        }
        return index.get(shortNameKey(stat.athlete().firstName(), stat.athlete().lastName(), stat.team()));
    }

    private static PlayerRef resolveLocalPlayer(Map<String, PlayerRef> index, String fullName, String team) {
        PlayerRef local = index.get(normalizeName(fullName));
        if (local != null) {
            return local;
        }
        int space = StringUtils.trimToEmpty(fullName).lastIndexOf(' ');
        if (space <= 0) {
            return null;
        }
        return index.get(shortNameKey(fullName.substring(0, space), fullName.substring(space + 1), team));
    }

    private static void putPreferred(Map<String, PlayerRef> index, String key, PlayerRef candidate) {
        if (StringUtils.isBlank(key)) {
            return;
        }
        PlayerRef existing = index.get(key);
        if (existing == null || (!hasNbaPhoto(existing.photoUrl()) && hasNbaPhoto(candidate.photoUrl()))) {
            index.put(key, candidate);
        }
    }

    private static boolean hasNbaPhoto(String photoUrl) {
        return Strings.CS.startsWith(photoUrl, "https://cdn.nba.com");
    }

    /**
     * 姓名归一：去重音/标点/大小写与 Jr./Sr./II 等后缀（与排行榜同步口径一致）
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

    private static String shortNameKey(String firstName, String lastName, String team) {
        String first = normalizeName(firstName);
        String last = normalizeName(lastName);
        if (first.isEmpty() || last.isEmpty()) {
            return "";
        }
        return last + "|" + StringUtils.trimToEmpty(team) + "|" + first.charAt(0);
    }

    private static int resolveSeason() {
        LocalDate now = LocalDate.now();
        return now.getMonthValue() >= SEASON_SWITCH_MONTH ? now.getYear() + 1 : now.getYear();
    }

    private record PlayerRef(long playerId, String team, String photoUrl) {
    }
}
