package com.rivers.nba.repository;

import com.rivers.nba.entity.PlayerSeasonHistory;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * <p>
 * 球员历年数据 Repository（R2DBC）
 * </p>
 *
 * @author rivers
 * @since 2026-11-01
 */
public interface PlayerSeasonHistoryRepository extends ReactiveCrudRepository<PlayerSeasonHistory, Long> {

    @Query("""
            SELECT * FROM player_season_history WHERE is_deleted = 0 AND player_id = :playerId
            ORDER BY season DESC, season_type ASC, id ASC
            """)
    Flux<PlayerSeasonHistory> findByPlayerId(long playerId);

    @Query("""
            SELECT COUNT(*) FROM player_season_history
            WHERE is_deleted = 0 AND season = :season AND season_type = :seasonType
            """)
    Mono<Long> countBySeasonType(int season, int seasonType);

    @Modifying
    @Query("DELETE FROM player_season_history WHERE season = :season AND season_type = :seasonType")
    Mono<Long> deleteBySeasonType(int season, int seasonType);
}
