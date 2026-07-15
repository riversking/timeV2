package com.rivers.approval.repository;

import com.rivers.approval.entity.FlowDefinition;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface FlowDefinitionRepository extends ReactiveCrudRepository<FlowDefinition, Long> {

    /**
     * 根据定义标识查询最新版本（已发布 & 未删除）
     */
    @Query("""
        SELECT * FROM flow_definition
        WHERE definition_key = :key
          AND is_deleted = 0
          AND status = 'PUBLISHED'
        ORDER BY version DESC
        LIMIT 1
        """)
    Mono<FlowDefinition> findLatestPublishedByKey(String key);

    /**
     * 根据定义标识和版本号精确查询（未删除）
     */
    @Query("""
        SELECT * FROM flow_definition
        WHERE definition_key = :key
          AND version = :version
          AND is_deleted = 0
        """)
    Mono<FlowDefinition> findByKeyAndVersion(String key, Integer version);

    /**
     * 分页查询所有已发布流程定义（按更新时间倒序）
     */
    @Query("""
        SELECT * FROM flow_definition
        WHERE status = :status
          AND is_deleted = 0
        ORDER BY update_time DESC
        LIMIT :size OFFSET :offset
        """)
    Flux<FlowDefinition> findByStatusWithPage(String status, int offset, int size);

    /**
     * 统计指定状态的流程定义数量
     */
    @Query("""
        SELECT COUNT(*) FROM flow_definition
        WHERE status = :status AND is_deleted = 0
        """)
    Mono<Long> countByStatus(String status);
}
