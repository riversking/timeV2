package com.rivers.nba.repository;

import com.rivers.nba.entity.TimerStadium;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * <p>
 * 球馆信息表 Repository（R2DBC）
 * </p>
 *
 * @author rivers
 * @since 2026-10-02
 */
public interface StadiumRepository extends ReactiveCrudRepository<TimerStadium, Long> {

    /**
     * 分页查询（过滤逻辑删除，按球馆名称模糊匹配）
     */
    @Query("SELECT * FROM stadium WHERE is_deleted = 0 AND name LIKE CONCAT('%', :name, '%') ORDER BY id LIMIT :limit OFFSET :offset")
    Flux<TimerStadium> pageByName(String name, int limit, long offset);

    /**
     * 分页总数（过滤逻辑删除）
     */
    @Query("SELECT COUNT(*) FROM stadium WHERE is_deleted = 0 AND name LIKE CONCAT('%', :name, '%')")
    Mono<Long> countByName(String name);

    /**
     * 按球馆ID查询单条（过滤逻辑删除）
     */
    @Query("SELECT * FROM stadium WHERE stadium_id = :stadiumId AND is_deleted = 0 LIMIT 1")
    Mono<TimerStadium> findByStadiumId(int stadiumId);

    /**
     * 逻辑删除全表（is_deleted 置 1）
     */
    @Modifying
    @Query("UPDATE stadium SET is_deleted = 1 WHERE is_deleted = 0")
    Mono<Long> logicDeleteAll();
}
