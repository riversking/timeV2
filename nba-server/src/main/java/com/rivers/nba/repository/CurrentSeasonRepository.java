package com.rivers.nba.repository;

import com.rivers.nba.entity.CurrentSeason;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * <p>
 * 当前赛季信息表 Repository（R2DBC）
 * </p>
 *
 * @author rivers
 * @since 2026-10-02
 */
public interface CurrentSeasonRepository extends ReactiveCrudRepository<CurrentSeason, Long> {

    @Query("SELECT * FROM current_season WHERE is_deleted = 0 ORDER BY id DESC LIMIT 1")
    Mono<CurrentSeason> findActive();

    @Query("SELECT COUNT(*) FROM current_season WHERE is_deleted = 0")
    Mono<Long> countActive();

    @Query("SELECT * FROM current_season WHERE is_deleted = 0 ORDER BY id DESC LIMIT :limit OFFSET :offset")
    Flux<CurrentSeason> pageAll(int limit, long offset);

    @Modifying
    @Query("UPDATE current_season SET is_deleted = 1 WHERE is_deleted = 0")
    Mono<Long> logicDeleteAll();
}
