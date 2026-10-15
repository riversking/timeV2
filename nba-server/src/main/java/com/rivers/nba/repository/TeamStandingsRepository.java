package com.rivers.nba.repository;

import com.rivers.nba.entity.TeamStanding;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * <p>
 * 球队赛季排名 Repository（R2DBC）
 * </p>
 *
 * @author rivers
 * @since 2026-10-16
 */
public interface TeamStandingsRepository extends ReactiveCrudRepository<TeamStanding, Long> {

    @Query("SELECT * FROM team_standings WHERE is_deleted = 0 AND season = :season ORDER BY id ASC")
    Flux<TeamStanding> findBySeason(int season);

    /**
     * 库内最新（有数据）赛季，空表返回 0
     */
    @Query("SELECT COALESCE(MAX(season), 0) FROM team_standings WHERE is_deleted = 0")
    Mono<Integer> latestSeason();

    /**
     * 按 (season, team_id) 唯一键 upsert：已存在则更新战绩字段，不存在则插入
     */
    @Modifying
    @Query("""
            INSERT INTO team_standings (season, team_id, team_key, city, name, conference, division, wins, losses,
            games_back, last_ten_wins, last_ten_losses, streak_description, points_per_game_for,
            points_per_game_against, create_user, update_user, is_deleted)
            VALUES (:#{#row.season}, :#{#row.teamId}, :#{#row.teamKey}, :#{#row.city}, :#{#row.name},
            :#{#row.conference}, :#{#row.division}, :#{#row.wins}, :#{#row.losses}, :#{#row.gamesBack},
            :#{#row.lastTenWins}, :#{#row.lastTenLosses}, :#{#row.streakDescription},
            :#{#row.pointsPerGameFor}, :#{#row.pointsPerGameAgainst}, 'system', 'system', 0)
            ON DUPLICATE KEY UPDATE team_key = :#{#row.teamKey}, city = :#{#row.city}, name = :#{#row.name},
            conference = :#{#row.conference}, division = :#{#row.division}, wins = :#{#row.wins},
            losses = :#{#row.losses}, games_back = :#{#row.gamesBack}, last_ten_wins = :#{#row.lastTenWins},
            last_ten_losses = :#{#row.lastTenLosses}, streak_description = :#{#row.streakDescription},
            points_per_game_for = :#{#row.pointsPerGameFor},
            points_per_game_against = :#{#row.pointsPerGameAgainst}, update_user = 'system', is_deleted = 0
            """)
    Mono<Long> upsert(@Param("row") TeamStanding row);
}
