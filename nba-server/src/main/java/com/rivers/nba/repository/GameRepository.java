package com.rivers.nba.repository;

import com.rivers.nba.entity.Game;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * <p>
 * 比赛信息表 Repository（R2DBC）
 * </p>
 *
 * @author rivers
 * @since 2026-10-02
 */
public interface GameRepository extends ReactiveCrudRepository<Game, Long> {

    @Query("""
            SELECT * FROM game WHERE is_deleted = 0
            AND (:teamId = 0 OR away_team_id = :teamId OR home_team_id = :teamId)
            AND (:season = 0 OR season = :season)
            AND (:date = '' OR DATE_FORMAT(day, '%Y-%m-%d') = :date)
            ORDER BY day ASC, id ASC LIMIT :limit OFFSET :offset
            """)
    Flux<Game> pageByFilter(int teamId, int season, String date, int limit, long offset);

    @Query("""
            SELECT COUNT(*) FROM game WHERE is_deleted = 0
            AND (:teamId = 0 OR away_team_id = :teamId OR home_team_id = :teamId)
            AND (:season = 0 OR season = :season)
            AND (:date = '' OR DATE_FORMAT(day, '%Y-%m-%d') = :date)
            """)
    Mono<Long> countByFilter(int teamId, int season, String date);

    @Query("SELECT * FROM game WHERE is_deleted = 0 AND day >= NOW() ORDER BY day ASC, id ASC LIMIT :limit")
    Flux<Game> findUpcoming(int limit);

    @Query("""
            SELECT * FROM game WHERE is_deleted = 0 AND DATE_FORMAT(day, '%Y-%m') = :month
            AND (:season = 0 OR season = :season)
            AND (:teamId = 0 OR away_team_id = :teamId OR home_team_id = :teamId)
            ORDER BY day ASC, id ASC
            """)
    Flux<Game> findByMonth(String month, int season, int teamId);

    @Modifying
    @Query("UPDATE game SET is_deleted = 1 WHERE is_deleted = 0 AND season = :season")
    Mono<Long> logicDeleteBySeason(int season);

    @Modifying
    @Query("UPDATE game SET is_deleted = 1 WHERE is_deleted = 0 AND DATE(day) = :date")
    Mono<Long> logicDeleteByDay(String date);
}
