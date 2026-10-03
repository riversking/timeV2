package com.rivers.nba.repository;

import com.rivers.nba.entity.Referee;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * <p>
 * 裁判信息表 Repository（R2DBC）
 * </p>
 *
 * @author rivers
 * @since 2026-10-02
 */
public interface RefereeRepository extends ReactiveCrudRepository<Referee, Long> {

    @Query("SELECT * FROM referee WHERE is_deleted = 0 AND name LIKE CONCAT('%', :name, '%') ORDER BY id LIMIT :limit OFFSET :offset")
    Flux<Referee> pageByName(String name, int limit, long offset);

    @Query("SELECT COUNT(*) FROM referee WHERE is_deleted = 0 AND name LIKE CONCAT('%', :name, '%')")
    Mono<Long> countByName(String name);

    @Modifying
    @Query("UPDATE referee SET is_deleted = 1 WHERE is_deleted = 0")
    Mono<Long> logicDeleteAll();
}
