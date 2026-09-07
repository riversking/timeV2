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
     * 查某人的待办任务：
     * 已认领的 + 无需领单模式（task_mode <> CLAIM）的待办理行
     */
    @Query("""
            SELECT * FROM flow_task
            WHERE assignee = :userId
              AND is_deleted = 0
              AND (status = 'CLAIMED'
                   OR (status = 'PENDING' AND task_mode <> 'CLAIM'))
            ORDER BY priority DESC, create_time DESC
            LIMIT :size OFFSET :offset
            """)
    Flux<FlowTask> findTodoByUserWithPage(String userId, int offset, int size);

    /**
     * 查待认领池（仅需领单模式 CLAIM 的 PENDING 行）
     */
    @Query("""
            SELECT * FROM flow_task
            WHERE assignee = :userId
              AND status = 'PENDING'
              AND task_mode = 'CLAIM'
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
     * 认领任务（PENDING → CLAIMED，仅 CLAIM 模式，assignee 指向自己即具备认领资格）
     */
    @Modifying
    @Query("""
            UPDATE flow_task
            SET status = 'CLAIMED',
                claimed_time = NOW(),
                update_user = :userId
            WHERE id = :id
              AND status = 'PENDING'
              AND task_mode = 'CLAIM'
              AND assignee = :userId
              AND is_deleted = 0
            """)
    Mono<Integer> claim(@Param("id") Long id, @Param("userId") String userId);

    /**
     * 认领/或签成功后清理同节点实例的其他候选行
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
     * 完成任务标记（CLAIMED → COMPLETED，仅需领单模式，只有认领人能完成）
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
     * 无需领单直接完成（PENDING → COMPLETED，ANY_ONE/ALL/SEQUENTIAL 模式）
     */
    @Modifying
    @Query("""
            UPDATE flow_task
            SET status = 'COMPLETED',
                update_user = :userId
            WHERE id = :id
              AND status = 'PENDING'
              AND assignee = :userId
              AND is_deleted = 0
            """)
    Mono<Integer> completeNoClaim(@Param("id") Long id, @Param("userId") String userId);

    /**
     * 统计节点实例剩余活跃任务（PENDING / CLAIMED）。
     * 事务提交后当前读调用：0 表示节点全部办理完成，可推进。
     */
    @Query("""
            SELECT COUNT(*) FROM flow_task
            WHERE node_instance_id = :nodeInstanceId
              AND status IN ('PENDING', 'CLAIMED')
              AND is_deleted = 0
            """)
    Mono<Long> countActiveByNodeInstanceId(@Param("nodeInstanceId") Long nodeInstanceId);

    /**
     * 串签：激活下一顺位（WAITING → PENDING），返回 0 表示没有下一顺位
     */
    @Modifying
    @Query("""
            UPDATE flow_task
            SET status = 'PENDING',
                update_user = 'SYSTEM'
            WHERE node_instance_id = :nodeInstanceId
              AND seq = :nextSeq
              AND status = 'WAITING'
              AND is_deleted = 0
            """)
    Mono<Integer> activateNextWaiting(@Param("nodeInstanceId") Long nodeInstanceId,
                                      @Param("nextSeq") int nextSeq);

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
              AND status IN ('PENDING', 'CLAIMED', 'WAITING')
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