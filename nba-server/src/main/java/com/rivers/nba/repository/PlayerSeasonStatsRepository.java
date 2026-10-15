package com.rivers.nba.repository;

import com.rivers.nba.entity.PlayerSeasonStat;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * <p>
 * 球员赛季统计 Repository（R2DBC）
 * </p>
 *
 * @author rivers
 * @since 2026-10-16
 */
public interface PlayerSeasonStatsRepository extends ReactiveCrudRepository<PlayerSeasonStat, Long> {

    @Query("SELECT * FROM player_season_stats WHERE is_deleted = 0 AND season = :season ORDER BY id ASC")
    Flux<PlayerSeasonStat> findBySeason(int season);

    /**
     * 库内最新（有数据）赛季，空表返回 0
     */
    @Query("SELECT COALESCE(MAX(season), 0) FROM player_season_stats WHERE is_deleted = 0")
    Mono<Integer> latestSeason();

    /**
     * 按 (season, player_id) 唯一键 upsert：已存在则更新统计字段，不存在则插入
     */
    @Modifying
    @Query("""
            INSERT INTO player_season_stats (season, player_id, player_name, team, position, games, points,
            rebounds, assists, steals, blocked_shots, three_pointers_made, field_goals_made, field_goals_attempted,
            create_user, update_user, is_deleted)
            VALUES (:#{#row.season}, :#{#row.playerId}, :#{#row.playerName}, :#{#row.team}, :#{#row.position},
            :#{#row.games}, :#{#row.points}, :#{#row.rebounds}, :#{#row.assists}, :#{#row.steals},
            :#{#row.blockedShots}, :#{#row.threePointersMade}, :#{#row.fieldGoalsMade},
            :#{#row.fieldGoalsAttempted}, 'system', 'system', 0)
            ON DUPLICATE KEY UPDATE player_name = :#{#row.playerName}, team = :#{#row.team},
            position = :#{#row.position}, games = :#{#row.games}, points = :#{#row.points},
            rebounds = :#{#row.rebounds}, assists = :#{#row.assists}, steals = :#{#row.steals},
            blocked_shots = :#{#row.blockedShots}, three_pointers_made = :#{#row.threePointersMade},
            field_goals_made = :#{#row.fieldGoalsMade}, field_goals_attempted = :#{#row.fieldGoalsAttempted},
            update_user = 'system', is_deleted = 0
            """)
    Mono<Long> upsert(@Param("row") PlayerSeasonStat row);
}
