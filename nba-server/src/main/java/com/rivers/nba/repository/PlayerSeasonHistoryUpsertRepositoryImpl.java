package com.rivers.nba.repository;

import com.rivers.nba.entity.PlayerSeasonHistory;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.rivers.core.util.ValueUtils.defaultDouble;
import static com.rivers.core.util.ValueUtils.defaultInt;
import static com.rivers.core.util.ValueUtils.defaultString;

/**
 * <p>
 * 球员历年数据批量写入实现（先删整季整类再写入，按唯一键 upsert 兜底并发）
 * </p>
 *
 * @author rivers
 * @since 2026-11-02
 */
@Repository
public class PlayerSeasonHistoryUpsertRepositoryImpl implements PlayerSeasonHistoryUpsertRepository {

    /**
     * 单条 SQL 的行数上限，避免占位符参数过多
     */
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

    private final DatabaseClient databaseClient;

    public PlayerSeasonHistoryUpsertRepositoryImpl(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public Mono<Void> upsertAll(List<PlayerSeasonHistory> rows) {
        if (rows.isEmpty()) {
            return Mono.empty();
        }
        return Flux.fromIterable(partition(rows))
                .concatMap(this::upsertBatch)
                .then();
    }

    private Mono<Void> upsertBatch(List<PlayerSeasonHistory> rows) {
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
}
