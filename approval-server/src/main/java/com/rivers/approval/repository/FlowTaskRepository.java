package com.rivers.approval.repository;

import com.rivers.approval.entity.FlowTask;
import org.springframework.data.r2dbc.repository.Modifying;
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
     * 查询某实例下所有活跃任务
     */
    @Query("""
            SELECT * FROM flow_task
            WHERE instance_id = :instanceId
            AND is_deleted = 0
            ORDER BY create_time ASC
            """)
    Flux<FlowTask> findByInstanceId(Long instanceId);

    /**
     * 查询节点实例对应的任务（多候选人时一对多）
     */
    @Query("SELECT * FROM flow_task WHERE node_instance_id = :nodeInstanceId AND is_deleted = 0")
    Flux<FlowTask> findByNodeInstanceId(Long nodeInstanceId);

    /**
     * 查某人的待办任务（已认领，未完成）
     */
    @Query("""
            SELECT * FROM flow_task
            WHERE assignee = :userId
              AND status = 'CLAIMED'
              AND is_deleted = 0
            ORDER BY priority DESC, create_time DESC
            LIMIT :size OFFSET :offset
            """)
    Flux<FlowTask> findTodoByUserWithPage(String userId, int offset, int size);

    /**
     * 查待认领池（assignee 指向自己的 PENDING 行）
     */
    @Query("""
            SELECT * FROM flow_task
            WHERE assignee = :userId
              AND status = 'PENDING'
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
            WHERE assignee = :userId
              AND status IN ('PENDING', 'CLAIMED')
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

    // ========== 任务操作（CAS 标记 → 归档 → 物理删除，三步由 Service 事务编排） ==========

    /**
     * 认领任务（PENDING → CLAIMED，assignee 指向自己即具备认领资格）
     */
    @Modifying
    @Query("""
            UPDATE flow_task
            SET status = 'CLAIMED',
                claimed_time = NOW(),
                update_user = :userId
            WHERE id = :id
              AND status = 'PENDING'
              AND assignee = :userId
              AND is_deleted = 0
            """)
    Mono<Integer> claim(@Param("id") Long id, @Param("userId") String userId);

    /**
     * 认领成功后清理同节点实例的其他候选行
     */
    @Modifying
    @Query("""
            DELETE FROM flow_task
            WHERE node_instance_id = :nodeInstanceId
              AND status = 'PENDING'
              AND id <> :claimedId
              AND is_deleted = 0
            """)
    Mono<Integer> deleteOtherPending(@Param("nodeInstanceId") Long nodeInstanceId,
                                     @Param("claimedId") Long claimedId);

    /**
     * 完成任务标记（CLAIMED → COMPLETED，只有认领人能完成）
     */
    @Modifying
    @Query("""
            UPDATE flow_task
            SET status = 'COMPLETED',
                update_user = :userId
            WHERE id = :id
              AND status = 'CLAIMED'
              AND assignee = :userId
              AND is_deleted = 0
            """)
    Mono<Integer> completeTask(@Param("id") Long id, @Param("userId") String userId);

    /**
     * 取消任务标记
     */
    @Modifying
    @Query("""
            UPDATE flow_task
            SET status = 'CANCELLED',
                update_user = :operator
            WHERE id = :id
              AND status IN ('PENDING', 'CLAIMED')
              AND is_deleted = 0
            """)
    Mono<Integer> cancelTask(@Param("id") Long id, @Param("operator") String operator);

    /**
     * 转出标记（CLAIMED → TRANSFERRED，只有认领人能转交）
     */
    @Modifying
    @Query("""
            UPDATE flow_task
            SET status = 'TRANSFERRED',
                update_user = :operator
            WHERE id = :id
              AND status = 'CLAIMED'
              AND assignee = :operator
              AND is_deleted = 0
            """)
    Mono<Integer> transferOut(@Param("id") Long id, @Param("operator") String operator);

    /**
     * 归档后物理删除待办行（status 条件防误删）
     */
    @Modifying
    @Query("""
            DELETE FROM flow_task
            WHERE id = :id AND status = :status
            AND is_deleted = 0
            """)
    Mono<Integer> deleteByIdAndStatus(@Param("id") Long id, @Param("status") String status);

    /**
     * 实例终止：冻结全部活跃任务（CAS 标记，后续归档删除）
     */
    @Modifying
    @Query("""
            UPDATE flow_task
            SET status = 'CANCELLED',
                update_user = :operator
            WHERE instance_id = :instanceId
              AND status IN ('PENDING', 'CLAIMED')
              AND is_deleted = 0
            """)
    Mono<Integer> cancelActiveByInstanceId(@Param("instanceId") Long instanceId,
                                           @Param("operator") String operator);

    /**
     * 实例终止：归档后批量物理删除
     */
    @Modifying
    @Query("""
            DELETE FROM flow_task
            WHERE instance_id = :instanceId AND status = 'CANCELLED' AND is_deleted = 0
            """)
    Mono<Integer> deleteCancelledByInstanceId(@Param("instanceId") Long instanceId);
}