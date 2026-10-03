package com.rivers.nba.repository;

import com.rivers.nba.entity.DepthChart;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * <p>
 * 阵容深度表 Repository（R2DBC）
 * </p>
 *
 * @author rivers
 * @since 2026-10-02
 */
public interface DepthChartRepository extends ReactiveCrudRepository<DepthChart, Long> {

    @Query("SELECT * FROM depth_chart WHERE is_deleted = 0 ORDER BY id LIMIT :limit OFFSET :offset")
    Flux<DepthChart> pageAll(int limit, long offset);

    @Query("SELECT COUNT(*) FROM depth_chart WHERE is_deleted = 0")
    Mono<Long> countAll();

    @Query("SELECT * FROM depth_chart WHERE is_deleted = 0 AND team_id = :teamId ORDER BY id LIMIT :limit OFFSET :offset")
    Flux<DepthChart> pageByTeam(int teamId, int limit, long offset);

    @Query("SELECT COUNT(*) FROM depth_chart WHERE is_deleted = 0 AND team_id = :teamId")
    Mono<Long> countByTeam(int teamId);

    @Modifying
    @Query("UPDATE depth_chart SET is_deleted = 1 WHERE is_deleted = 0")
    Mono<Long> logicDeleteAll();
}
