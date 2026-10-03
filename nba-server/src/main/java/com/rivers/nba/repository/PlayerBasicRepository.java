package com.rivers.nba.repository;

import com.rivers.nba.entity.PlayerBasic;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * <p>
 * 球员简档信息表 Repository（R2DBC）
 * </p>
 *
 * @author rivers
 * @since 2026-10-02
 */
public interface PlayerBasicRepository extends ReactiveCrudRepository<PlayerBasic, Long> {

    @Query("SELECT * FROM player_basic WHERE is_deleted = 0 AND source = :source AND (last_name LIKE CONCAT('%', :name, '%') OR first_name LIKE CONCAT('%', :name, '%') OR draft_kings_name LIKE CONCAT('%', :name, '%')) ORDER BY id LIMIT :limit OFFSET :offset")
    Flux<PlayerBasic> pageBySource(int source, String name, int limit, long offset);

    @Query("SELECT COUNT(*) FROM player_basic WHERE is_deleted = 0 AND source = :source AND (last_name LIKE CONCAT('%', :name, '%') OR first_name LIKE CONCAT('%', :name, '%') OR draft_kings_name LIKE CONCAT('%', :name, '%'))")
    Mono<Long> countBySource(int source, String name);

    @Modifying
    @Query("UPDATE player_basic SET is_deleted = 1 WHERE is_deleted = 0 AND source = :source")
    Mono<Long> logicDeleteBySource(int source);
}
