package com.rivers.approval.repository;

import com.rivers.approval.entity.FlowTaskDone;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface FlowTaskDoneRepository extends ReactiveCrudRepository<FlowTaskDone, Long> {

    /**
     * 实例下全部已办任务（流程跟踪）
     */
    @Query("""
            SELECT * FROM flow_task_done
            WHERE instance_id = :instanceId
            AND is_deleted = 0
            ORDER BY end_time ASC
            """)
    Flux<FlowTaskDone> findByInstanceId(Long instanceId);

    /**
     * 某人的已办列表
     */
    @Query("""
            SELECT * FROM flow_task_done
            WHERE assignee = :userId
            AND is_deleted = 0
            ORDER BY end_time DESC
            LIMIT :size OFFSET :offset
            """)
    Flux<FlowTaskDone> findDoneByUserWithPage(String userId, int offset, int size);

    /**
     * 单任务归档：待办行快照迁入已办表。
     * WHERE id + status 提供 CAS 语义（行必须先被标记为该终态）
     */
    @Modifying
    @Query("""
            INSERT INTO flow_task_done
                (id, instance_id, node_instance_id, task_no, task_name, status, assignee,
                 priority, due_time, claimed_time, result, comment, prev_task_id,
                 create_user, update_user)
            SELECT id, instance_id, node_instance_id, task_no, task_name, :status, assignee,
                   priority, due_time, claimed_time, :result, :comment, prev_task_id,
                   create_user, :operator
            FROM flow_task
            WHERE id = :id AND status = :status
            """)
    Mono<Integer> archiveById(@Param("id") Long id,
                              @Param("status") String status,
                              @Param("result") String result,
                              @Param("comment") String comment,
                              @Param("operator") String operator);

    /**
     * 实例终止时批量归档全部取消任务
     */
    @Modifying
    @Query("""
            INSERT INTO flow_task_done
                (id, instance_id, node_instance_id, task_no, task_name, status, assignee,
                 priority, due_time, claimed_time, end_time, result, comment, prev_task_id,
                 create_user, update_user)
            SELECT id, instance_id, node_instance_id, task_no, task_name, 'CANCELLED', assignee,
                   priority, due_time, claimed_time, NOW(), 'CANCELLED', '', prev_task_id,
                   create_user, :operator
            FROM flow_task
            WHERE instance_id = :instanceId AND status = 'CANCELLED'
            """)
    Mono<Integer> archiveCancelledByInstanceId(@Param("instanceId") Long instanceId,
                                               @Param("operator") String operator);
}