package com.rivers.approval.repository;

import com.rivers.approval.entity.FlowTask;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface FlowTaskRepository extends ReactiveCrudRepository<FlowTask, Long> {

    /**
     * 根据任务编号查询
     */
    @Query("SELECT * FROM flow_task WHERE task_no = :taskNo AND is_deleted = 0")
    Mono<FlowTask> findByTaskNo(String taskNo);

    /**
     * 查询某实例下所有任务
     */
    @Query("""
        SELECT * FROM flow_task
        WHERE instance_id = :instanceId
          AND is_deleted = 0
        ORDER BY create_time ASC
        """)
    Flux<FlowTask> findByInstanceId(Long instanceId);

    /**
     * 查询节点实例对应的任务（一对一）
     */
    @Query("""
        SELECT * FROM flow_task
        WHERE node_instance_id = :nodeInstanceId
          AND is_deleted = 0
        """)
    Mono<FlowTask> findByNodeInstanceId(Long nodeInstanceId);

    /**
     * 查某人的待办任务（已认领，未完成）
     */
    @Query("""
        SELECT * FROM flow_task
        WHERE claimed_by = :userId
          AND status = 'CLAIMED'
          AND is_deleted = 0
        ORDER BY priority DESC, create_time DESC
        LIMIT :size OFFSET :offset
        """)
    Flux<FlowTask> findTodoByUserWithPage(String userId, int offset, int size);

    /**
     * 查待认领池（状态 PENDING，候选人包含该用户）
     * 注意: candidate_users 是 JSON 数组字符串，用 JSON_CONTAINS 匹配
     */
    @Query("""
        SELECT * FROM flow_task
        WHERE status = 'PENDING'
          AND JSON_CONTAINS(candidate_users, JSON_QUOTE(:userId))
          AND is_deleted = 0
        ORDER BY priority DESC, create_time DESC
        LIMIT :size OFFSET :offset
        """)
    Flux<FlowTask> findClaimableByUserWithPage(String userId, int offset, int size);

    /**
     * 统计某人的待办/待认领数量
     */
    @Query("""
        SELECT COUNT(*) FROM flow_task
        WHERE ((claimed_by = :userId AND status = 'CLAIMED')
            OR (status = 'PENDING' AND JSON_CONTAINS(candidate_users, JSON_QUOTE(:userId))))
          AND is_deleted = 0
        """)
    Mono<Long> countPendingByUser(String userId);

    /**
     * 按实例ID和状态统计任务数
     */
    @Query("""
        SELECT COUNT(*) FROM flow_task
        WHERE instance_id = :instanceId
          AND status = :status
          AND is_deleted = 0
        """)
    Mono<Long> countByInstanceIdAndStatus(Long instanceId, String status);

    // ========== 任务操作 ==========

    /**
     * 认领任务（PENDING → CLAIMED，需 CAS 防并发）
     */
    @Query("""
        UPDATE flow_task
        SET status = 'CLAIMED',
            claimed_by = :userId,
            claimed_time = NOW(),
            update_user = :userId,
            update_time = NOW()
        WHERE id = :id
          AND status = 'PENDING'
          AND is_deleted = 0
          AND JSON_CONTAINS(candidate_users, JSON_QUOTE(:userId))
        """)
    Mono<Integer> claim(@Param("id") Long id, @Param("userId") String userId);

    /**
     * 完成任务（CLAIMED → COMPLETED）
     */
    @Query("""
        UPDATE flow_task
        SET status = 'COMPLETED',
            result = :result,
            comment = :comment,
            completed_by = :userId,
            completed_time = NOW(),
            update_user = :userId,
            update_time = NOW()
        WHERE id = :id
          AND status = 'CLAIMED'
          AND claimed_by = :userId
          AND is_deleted = 0
        """)
    Mono<Integer> complete(
            @Param("id") Long id,
            @Param("result") String result,
            @Param("comment") String comment,
            @Param("userId") String userId);

    /**
     * 取消任务
     */
    @Query("""
        UPDATE flow_task
        SET status = 'CANCELLED',
            update_user = :operator,
            update_time = NOW()
        WHERE id = :id
          AND status IN ('PENDING', 'CLAIMED')
          AND is_deleted = 0
        """)
    Mono<Integer> cancel(@Param("id") Long id, @Param("operator") String operator);

    /**
     * 转交任务（CLAIMED → PENDING，保留前驱引用）
     */
    @Query("""
        UPDATE flow_task
        SET status = 'TRANSFERRED',
            update_user = :operator,
            update_time = NOW()
        WHERE id = :id
          AND status = 'CLAIMED'
          AND claimed_by = :operator
          AND is_deleted = 0
        """)
    Mono<Integer> transferOut(@Param("id") Long id, @Param("operator") String operator);
}
