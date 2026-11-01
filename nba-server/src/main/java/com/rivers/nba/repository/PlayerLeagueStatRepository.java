package com.rivers.nba.repository;

import com.rivers.nba.entity.PlayerLeagueStat;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * <p>
 * 球员赛季联盟对比 Repository（R2DBC）
 * </p>
 *
 * @author rivers
 * @since 2026-11-01
 */
public interface PlayerLeagueStatRepository extends ReactiveCrudRepository<PlayerLeagueStat, Long> {

    /**
     * 球员全部赛季的联盟对比行（按赛季倒序、指标顺序）
     */
    @Query("""
            SELECT * FROM player_season_league_stat WHERE is_deleted = 0 AND player_id = :playerId
            ORDER BY season DESC, sort_no ASC, id ASC
            """)
    Flux<PlayerLeagueStat> findByPlayerId(long playerId);

    /**
     * 指定赛季已落库的对比行数（判断历史赛季是否需要回填）
     */
    @Query("SELECT COUNT(*) FROM player_season_league_stat WHERE is_deleted = 0 AND season = :season")
    Mono<Long> countBySeason(int season);

    /**
     * 清理指定赛季（同步后整季重算）
     */
    @Modifying
    @Query("DELETE FROM player_season_league_stat WHERE season = :season")
    Mono<Long> deleteBySeason(int season);
}
