package com.rivers.approval.repository;

import com.rivers.approval.entity.FlowTrack;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface FlowTrackRepository extends ReactiveCrudRepository<FlowTrack, Long> {

    /**
     * 实例跟踪链（入库顺序直出，零聚合）
     */
    @Query("""
            SELECT * FROM flow_track
            WHERE instance_id = :instanceId
              AND is_deleted = 0
            ORDER BY create_time ASC, id ASC
            """)
    Flux<FlowTrack> findByInstanceId(Long instanceId);

    /**
     * 环节的待审批行（审批时将其原地 UPDATE 为终态）
     */
    @Query("""
            SELECT * FROM flow_track
            WHERE instance_id = :instanceId
              AND node_instance_id = :nodeInstanceId
              AND track_type = 'PENDING'
              AND is_deleted = 0
            LIMIT 1
            """)
    Mono<FlowTrack> findPendingRow(Long instanceId, Long nodeInstanceId);

    /**
     * 审批落库：PENDING 行原地变终态（保留 task_time）
     */
    @Modifying
    @Query("""
            UPDATE flow_track
            SET track_type = :result,
                assignee = :assignee,
                action_time = NOW(),
                opinion = :opinion,
                update_user = :operator
            WHERE instance_id = :instanceId
              AND node_instance_id = :nodeInstanceId
              AND track_type = 'PENDING'
              AND is_deleted = 0
            """)
    Mono<Integer> completePendingRow(Long instanceId, Long nodeInstanceId,
                                     String result, String assignee,
                                     String opinion, String operator);

    /**
     * 多候选人：追加到环节行 assignee（去重）
     */
    @Modifying
    @Query("""
            UPDATE flow_track
            SET assignee = CASE WHEN FIND_IN_SET(:assignee, assignee) > 0
                                THEN assignee
                                ELSE CONCAT_WS(',', assignee, :assignee) END,
                update_user = :operator
            WHERE id = :id AND track_type = 'PENDING'
            """)
    Mono<Integer> appendAssignee(@Param("id") Long id,
                                 @Param("assignee") String assignee,
                                 @Param("operator") String operator);

    /**
     * 回填最近一行行为的"下一处理人"（下游任务创建时触发）
     */
    @Modifying
    @Query("""
            UPDATE flow_track
            SET next_assignee = :assignees
            WHERE id = (SELECT t.id FROM (SELECT id FROM flow_track
                                          WHERE instance_id = :instanceId
                                            AND is_deleted = 0
                                          ORDER BY id DESC LIMIT 1) t)
            """)
    Mono<Integer> backfillNextAssignee(Long instanceId, String assignees);
}