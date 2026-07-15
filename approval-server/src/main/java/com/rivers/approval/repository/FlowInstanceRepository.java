package com.rivers.approval.repository;

import com.rivers.approval.entity.FlowInstance;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface FlowInstanceRepository extends ReactiveCrudRepository<FlowInstance, Long> {

    /**
     * 根据实例编号查询（未删除）
     */
    @Query("SELECT * FROM flow_instance WHERE instance_no = :instanceNo AND is_deleted = 0")
    Mono<FlowInstance> findByInstanceNo(String instanceNo);

    /**
     * 查询某人发起的流程实例（分页，按发起时间倒序）
     */
    @Query("""
        SELECT * FROM flow_instance
        WHERE initiator = :initiator
          AND is_deleted = 0
        ORDER BY create_time DESC
        LIMIT :size OFFSET :offset
        """)
    Flux<FlowInstance> findByInitiatorWithPage(String initiator, int offset, int size);

    /**
     * 按状态查询流程实例（分页）
     */
    @Query("""
        SELECT * FROM flow_instance
        WHERE status = :status
          AND is_deleted = 0
        ORDER BY create_time DESC
        LIMIT :size OFFSET :offset
        """)
    Flux<FlowInstance> findByStatusWithPage(String status, int offset, int size);

    /**
     * 根据关联业务主键查询运行中的实例
     */
    @Query("""
        SELECT * FROM flow_instance
        WHERE business_key = :businessKey
          AND status = 'RUNNING'
          AND is_deleted = 0
        """)
    Flux<FlowInstance> findRunningByBusinessKey(String businessKey);

    /**
     * 更新实例状态（用于终止/完成操作）
     */
    @Query("""
        UPDATE flow_instance
        SET status = :status,
            end_time = :endTime,
            update_user = :operator,
            update_time = NOW()
        WHERE id = :id
          AND is_deleted = 0
        """)
    Mono<Integer> updateStatus(
            @Param("id") Long id,
            @Param("status") String status,
            @Param("endTime") java.time.LocalDateTime endTime,
            @Param("operator") String operator);

    /**
     * 更新当前活跃节点ID列表（流程推进时用）
     */
    @Query("""
        UPDATE flow_instance
        SET current_node_ids = :currentNodeIds,
            update_user = :operator,
            update_time = NOW()
        WHERE id = :id
          AND is_deleted = 0
        """)
    Mono<Integer> updateCurrentNodeIds(
            @Param("id") Long id,
            @Param("currentNodeIds") String currentNodeIds,
            @Param("operator") String operator);
}
