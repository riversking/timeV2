package com.rivers.nba.service.impl;

import com.rivers.nba.entity.PlayerLeagueStat;
import com.rivers.nba.entity.PlayerSeasonStat;
import com.rivers.nba.repository.PlayerLeagueStatRepository;
import com.rivers.nba.repository.PlayerSeasonStatsRepository;
import com.rivers.nba.service.ILeagueCompareService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import static com.rivers.core.util.ValueUtils.defaultInt;
import static com.rivers.core.util.ValueUtils.defaultLong;

/**
 * <p>
 * 球员赛季联盟对比 服务实现（同赛季场均 vs 联盟平均/最高，并给出名次）
 * 排名池按赛季最高场次的 25%（不低于 5 场）过滤；球员本人未达门槛时仍计入名次；
 * 库内该赛季数据不全（如兜底只落了单人）时不落对比行，避免出现「联盟第一 / 共 1 人」。
 * 数据在同步落库后整季预计算，球员详情直接读表，不在请求时聚合。
 * </p>
 *
 * @author rivers
 * @since 2026-11-01
 */
@Slf4j
@Service
public class LeagueCompareServiceImpl implements ILeagueCompareService {

    /**
     * 进入排名池的最少场次 = 赛季最高场次的 25%（且不低于 5 场）；赛季初不足 5 场时不做门槛过滤
     */
    private static final double QUALIFY_GAMES_RATIO = 0.25;
    private static final int MIN_QUALIFY_GAMES = 5;
    /**
     * 联盟对比的最少参与人数：库内该赛季数据不全（如兜底只落了单人）时不落对比行
     */
    private static final int MIN_LEAGUE_COMPARE_PLAYERS = 20;
    /**
     * 批量写入行数（避免单条 SQL 参数过多）
     */
    private static final int BATCH_SIZE = 300;
    private static final String INSERT_PREFIX = """
            INSERT INTO player_season_league_stat (season, player_id, stat_key, stat_label, sort_no,
            player_value, league_avg, league_max, rank_no, total_players, create_user, update_user, is_deleted)
            VALUES """;
    private static final String INSERT_VALUES = "(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'system', 'system', 0)";
    private static final String INSERT_SUFFIX = """
             AS new ON DUPLICATE KEY UPDATE stat_label = new.stat_label, sort_no = new.sort_no,
            player_value = new.player_value, league_avg = new.league_avg, league_max = new.league_max,
            rank_no = new.rank_no, total_players = new.total_players,
            update_user = 'system', is_deleted = 0
            """;

    private static final List<CompareStat> COMPARE_STATS = List.of(
            new CompareStat("points", "得分", PlayerSeasonStat::getPoints),
            new CompareStat("rebounds", "篮板", PlayerSeasonStat::getRebounds),
            new CompareStat("assists", "助攻", PlayerSeasonStat::getAssists),
            new CompareStat("steals", "抢断", PlayerSeasonStat::getSteals),
            new CompareStat("blocks", "盖帽", PlayerSeasonStat::getBlockedShots));

    private final PlayerSeasonStatsRepository playerSeasonStatsRepository;

    private final PlayerLeagueStatRepository playerLeagueStatRepository;

    private final DatabaseClient databaseClient;

    public LeagueCompareServiceImpl(PlayerSeasonStatsRepository playerSeasonStatsRepository,
                                    PlayerLeagueStatRepository playerLeagueStatRepository,
                                    DatabaseClient databaseClient) {
        this.playerSeasonStatsRepository = playerSeasonStatsRepository;
        this.playerLeagueStatRepository = playerLeagueStatRepository;
        this.databaseClient = databaseClient;
    }

    @Override
    public Flux<PlayerLeagueStat> findByPlayerId(long playerId) {
        return playerLeagueStatRepository.findByPlayerId(playerId);
    }

    @Override
    public Mono<Void> rebuildSeason(int season) {
        return playerSeasonStatsRepository.findBySeason(season)
                .collectList()
                .flatMap(rows -> replaceSeason(season, buildRows(season, rows)))
                .doOnError(e -> log.warn("联盟对比重算失败: season={}, error={}", season, e.getMessage()))
                .onErrorResume(e -> Mono.empty());
    }

    @Override
    public void rebuildSeasonAsync(int season) {
        rebuildSeason(season).subscribe();
    }

    @Override
    public Mono<Void> backfillMissing() {
        return playerSeasonStatsRepository.findSeasons()
                .concatMap(this::rebuildIfIncomplete)
                .then()
                .doOnError(e -> log.warn("联盟对比回填失败: {}", e.getMessage()))
                .onErrorResume(e -> Mono.empty());
    }

    /**
     * 已落库行数与赛季出场人数不匹配（含首次尚无对比表数据）时重算
     */
    private Mono<Void> rebuildIfIncomplete(int season) {
        return Mono.zip(playerSeasonStatsRepository.countPlayedBySeason(season),
                        playerLeagueStatRepository.countBySeason(season))
                .flatMap(counts -> {
                    long played = counts.getT1();
                    long stored = counts.getT2();
                    long expected = played >= MIN_LEAGUE_COMPARE_PLAYERS ? played * COMPARE_STATS.size() : 0;
                    if (stored == expected) {
                        return Mono.empty();
                    }
                    log.info("联盟对比数据缺失，按库内统计重算: season={}, played={}, stored={}",
                            season, played, stored);
                    return rebuildSeason(season);
                });
    }

    /**
     * 整季替换：先清理该赛季旧行，再分批写入重算结果
     */
    private Mono<Void> replaceSeason(int season, List<PlayerLeagueStat> rows) {
        return playerLeagueStatRepository.deleteBySeason(season)
                .then(rows.isEmpty() ? Mono.empty() : insertBatch(rows));
    }

    private Mono<Void> insertBatch(List<PlayerLeagueStat> rows) {
        return Flux.fromIterable(partition(rows))
                .concatMap(this::insertChunk)
                .then();
    }

    private static List<List<PlayerLeagueStat>> partition(List<PlayerLeagueStat> rows) {
        List<List<PlayerLeagueStat>> chunks = new ArrayList<>();
        for (int i = 0; i < rows.size(); i += BATCH_SIZE) {
            chunks.add(rows.subList(i, Math.min(rows.size(), i + BATCH_SIZE)));
        }
        return chunks;
    }

    private Mono<Void> insertChunk(List<PlayerLeagueStat> chunk) {
        String sql = INSERT_PREFIX + String.join(", ", Collections.nCopies(chunk.size(), INSERT_VALUES))
                + INSERT_SUFFIX;
        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(sql);
        int index = 0;
        for (PlayerLeagueStat row : chunk) {
            spec = spec.bind(index++, row.getSeason())
                    .bind(index++, row.getPlayerId())
                    .bind(index++, row.getStatKey())
                    .bind(index++, row.getStatLabel())
                    .bind(index++, row.getSortNo())
                    .bind(index++, row.getPlayerValue())
                    .bind(index++, row.getLeagueAvg())
                    .bind(index++, row.getLeagueMax())
                    .bind(index++, row.getRankNo())
                    .bind(index++, row.getTotalPlayers());
        }
        return spec.then();
    }

    /**
     * 按库内该赛季球员统计生成全部对比行（每位出场球员 × 五项指标）
     */
    private static List<PlayerLeagueStat> buildRows(int season, List<PlayerSeasonStat> rows) {
        List<PlayerSeasonStat> played = rows.stream()
                .filter(row -> defaultInt(row.getGames()) > 0)
                .toList();
        if (played.isEmpty()) {
            return List.of();
        }
        int maxGames = played.stream().mapToInt(row -> defaultInt(row.getGames())).max().orElse(0);
        int minGames = maxGames <= MIN_QUALIFY_GAMES
                ? 1
                : Math.max(MIN_QUALIFY_GAMES, (int) Math.ceil(maxGames * QUALIFY_GAMES_RATIO));
        List<PlayerSeasonStat> pool = played.stream()
                .filter(row -> defaultInt(row.getGames()) >= minGames)
                .toList();
        if (pool.size() < MIN_LEAGUE_COMPARE_PLAYERS) {
            return List.of();
        }
        List<PlayerLeagueStat> result = new ArrayList<>(played.size() * COMPARE_STATS.size());
        for (int i = 0; i < COMPARE_STATS.size(); i++) {
            CompareStat stat = COMPARE_STATS.get(i);
            double[] values = pool.stream()
                    .mapToDouble(row -> perGameRaw(stat.total().apply(row), defaultInt(row.getGames())))
                    .sorted()
                    .toArray();
            double avg = Arrays.stream(values).sum() / values.length;
            double max = values[values.length - 1];
            for (PlayerSeasonStat row : played) {
                int games = defaultInt(row.getGames());
                double selfValue = perGameRaw(stat.total().apply(row), games);
                int totalPlayers = games >= minGames ? pool.size() : pool.size() + 1;
                result.add(toCompareRow(season, i, stat, row, selfValue, avg, max,
                        aheadCount(values, selfValue) + 1, totalPlayers));
            }
        }
        return result;
    }

    private static PlayerLeagueStat toCompareRow(int season, int sortNo, CompareStat stat, PlayerSeasonStat row,
                                                 double selfValue, double avg, double max, int rank, int totalPlayers) {
        PlayerLeagueStat entity = new PlayerLeagueStat();
        entity.setSeason(season);
        entity.setPlayerId(defaultLong(row.getPlayerId()));
        entity.setStatKey(stat.key());
        entity.setStatLabel(stat.label());
        entity.setSortNo(sortNo);
        entity.setPlayerValue(roundOneDecimal(selfValue));
        entity.setLeagueAvg(roundOneDecimal(avg));
        entity.setLeagueMax(roundOneDecimal(max));
        entity.setRankNo(rank);
        entity.setTotalPlayers(totalPlayers);
        return entity;
    }

    /**
     * 升序数组中严格大于 self 的人数（名次 = 该人数 + 1）
     */
    private static int aheadCount(double[] sorted, double self) {
        int lo = 0;
        int hi = sorted.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (sorted[mid] <= self) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return sorted.length - lo;
    }

    private static double perGameRaw(Double total, int games) {
        return games <= 0 || total == null ? 0.0 : total / games;
    }

    private static double roundOneDecimal(double value) {
        return Math.round(value * 10) / 10.0;
    }

    /**
     * 联盟对比指标：key 与前端展示标识一致，total 取赛季总量
     */
    private record CompareStat(String key, String label, Function<PlayerSeasonStat, Double> total) {
    }
}
