package com.rivers.nba.repository;

import com.rivers.nba.entity.PlayerLeagueStat;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * <p>
 * 球员联盟对比批量写入实现（整季先删再写入，按唯一键 upsert 兜底并发）
 * </p>
 *
 * @author rivers
 * @since 2026-11-02
 */
@Repository
public class PlayerLeagueStatUpsertRepositoryImpl implements PlayerLeagueStatUpsertRepository {

    /**
     * 单条 SQL 的行数上限，避免占位符参数过多
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

    private final DatabaseClient databaseClient;

    public PlayerLeagueStatUpsertRepositoryImpl(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public Mono<Void> upsertAll(List<PlayerLeagueStat> rows) {
        if (rows.isEmpty()) {
            return Mono.empty();
        }
        return Flux.fromIterable(partition(rows))
                .concatMap(this::upsertBatch)
                .then();
    }

    private Mono<Void> upsertBatch(List<PlayerLeagueStat> rows) {
        String placeholders = String.join(", ", Collections.nCopies(rows.size(), INSERT_VALUES));
        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(INSERT_PREFIX + placeholders + INSERT_SUFFIX);
        int index = 0;
        for (PlayerLeagueStat row : rows) {
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
        return spec.fetch().rowsUpdated().then();
    }

    private static List<List<PlayerLeagueStat>> partition(List<PlayerLeagueStat> rows) {
        List<List<PlayerLeagueStat>> parts = new ArrayList<>();
        for (int i = 0; i < rows.size(); i += BATCH_SIZE) {
            parts.add(rows.subList(i, Math.min(rows.size(), i + BATCH_SIZE)));
        }
        return parts;
    }
}
