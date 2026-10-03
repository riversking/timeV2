package com.rivers.nba.repository;

import com.rivers.nba.entity.Team;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * <p>
 * 球队信息表 Repository（R2DBC）
 * </p>
 *
 * @author rivers
 * @since 2026-10-02
 */
public interface TeamRepository extends ReactiveCrudRepository<Team, Long> {

    @Query("SELECT * FROM team WHERE is_deleted = 0 AND (name LIKE CONCAT('%', :name, '%') OR city LIKE CONCAT('%', :name, '%')) ORDER BY id LIMIT :limit OFFSET :offset")
    Flux<Team> pageByName(String name, int limit, long offset);

    @Query("SELECT COUNT(*) FROM team WHERE is_deleted = 0 AND (name LIKE CONCAT('%', :name, '%') OR city LIKE CONCAT('%', :name, '%'))")
    Mono<Long> countByName(String name);

    /**
     * 按球队ID查询单条（过滤逻辑删除）
     */
    @Query("SELECT * FROM team WHERE team_id = :teamId AND is_deleted = 0 LIMIT 1")
    Mono<Team> findByTeamId(int teamId);

    @Modifying
    @Query("UPDATE team SET is_deleted = 1 WHERE is_deleted = 0")
    Mono<Long> logicDeleteAll();
}
