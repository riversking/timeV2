package com.rivers.approval.repository;

import com.rivers.approval.entity.FlowTrack;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 流程跟踪链仓储（行为发生即入库，查询零聚合）。
 * <p>
 * 展示模型：
 * <ul>
 *   <li>待领单 — 每个候选人一条独立 PENDING 行（不合并）</li>
 *   <li>认领   — 认领人的行原地流转为 CLAIMED（待审批）；其他候选人的行隐藏</li>
 *   <li>转交   — 环节最近活跃行原地流转为 TRANSFERRED（assignee=转交人，next=目标人）</li>
 *   <li>办理   — 办理人的活跃行原地流转为终态；接单人场景回退取最近活跃行</li>
 *   <li>next_assignee — 下游环节创建时回填到"上一环节最后有效行"（值为本环节候选聚合）</li>
 * </ul>
 */
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
     * 某人在该环节是否已有待领单行（TASK_CREATED 幂等：事件重投不插重复行；
     * FIND_IN_SET 兼容存量"userA,userB"合并行）
     */
    @Query("""
            SELECT * FROM flow_track
            WHERE instance_id = :instanceId
              AND node_instance_id = :nodeInstanceId
              AND track_type = 'PENDING'
              AND FIND_IN_SET(:assignee, assignee) > 0
              AND is_deleted = 0
            LIMIT 1
            """)
    Mono<FlowTrack> findPendingRowByAssignee(Long instanceId, Long nodeInstanceId,
                                             String assignee);

    /**
     * 环节已有"非待领单"行数（已认领/已转交/已办结）。
     * 用于拦截认领落地后才迟到的候选 TASK_CREATED 事件，避免补插多余待领单行。
     */
    @Query("""
            SELECT COUNT(*) FROM flow_track
            WHERE instance_id = :instanceId
              AND node_instance_id = :nodeInstanceId
              AND track_type <> 'PENDING'
              AND is_deleted = 0
            """)
    Mono<Long> countSettledRowsOfNode(Long instanceId, Long nodeInstanceId);

    /**
     * 环节最早行 id（回填锚点：其"之前最近一行"即上一环节行）
     */
    @Query("""
            SELECT id FROM flow_track
            WHERE instance_id = :instanceId
              AND node_instance_id = :nodeInstanceId
              AND is_deleted = 0
            ORDER BY id ASC
            LIMIT 1
            """)
    Mono<Long> firstRowIdOfNode(Long instanceId, Long nodeInstanceId);

    /**
     * 环节全部候选聚合（逗号拼接，回填上一行"下一处理人"用）
     */
    @Query("""
            SELECT GROUP_CONCAT(assignee ORDER BY id SEPARATOR ',') FROM flow_track
            WHERE instance_id = :instanceId
              AND node_instance_id = :nodeInstanceId
              AND track_type = 'PENDING'
              AND is_deleted = 0
            """)
    Mono<String> pendingAssigneesOfNode(Long instanceId, Long nodeInstanceId);

    /**
     * 回填"上一环节最后有效行"的 next_assignee（下游环节创建时触发）。
     * beforeId 传本环节最早行 id，确保回填目标是上一环节行而非本环节自身。
     */
    @Modifying
    @Query("""
            UPDATE flow_track
            SET next_assignee = :assignees
            WHERE id = (SELECT t.id FROM (SELECT id FROM flow_track
                                          WHERE instance_id = :instanceId
                                            AND id < :beforeId
                                            AND is_deleted = 0
                                          ORDER BY id DESC LIMIT 1) t)
            """)
    Mono<Integer> backfillNextAssignee(Long instanceId, Long beforeId, String assignees);

    /**
     * 认领原地流转：仅认领人的待领单行 → CLAIM（领单动作行）。
     * 待审批状态行（CLAIMED）由调用方追加；FIND_IN_SET 兼容存量合并行；next 置空后由前瞻回填。
     */
    @Modifying
    @Query("""
            UPDATE flow_track
            SET track_type = 'CLAIM',
                assignee = :assignee,
                next_assignee = NULL,
                action_time = NOW(),
                update_user = :operator
            WHERE instance_id = :instanceId
              AND node_instance_id = :nodeInstanceId
              AND track_type = 'PENDING'
              AND FIND_IN_SET(:assignee, assignee) > 0
              AND is_deleted = 0
            """)
    Mono<Integer> claimPendingRow(Long instanceId, Long nodeInstanceId,
                                  String assignee, String operator);

    /**
     * 更新环节最后有效行的 next_assignee（认领后前瞻展示下一环节处理人用）。
     */
    @Modifying
    @Query("""
            UPDATE flow_track
            SET next_assignee = :assignees,
                update_user = :operator
            WHERE id = (SELECT t.id FROM (SELECT id FROM flow_track
                                          WHERE instance_id = :instanceId
                                            AND node_instance_id = :nodeInstanceId
                                            AND is_deleted = 0
                                          ORDER BY id DESC LIMIT 1) t)
            """)
    Mono<Integer> setNextOfNode(Long instanceId, Long nodeInstanceId,
                                String assignees, String operator);

    /**
     * 认领落地后：隐藏同环节其他候选人的待领单行（"A 领单完 B 不展示"）
     */
    @Modifying
    @Query("""
            UPDATE flow_track
            SET is_deleted = 1, update_user = :operator
            WHERE instance_id = :instanceId
              AND node_instance_id = :nodeInstanceId
              AND track_type = 'PENDING'
              AND FIND_IN_SET(:assignee, assignee) = 0
              AND is_deleted = 0
            """)
    Mono<Integer> hideOtherPendingRows(Long instanceId, Long nodeInstanceId,
                                       String assignee, String operator);

    /**
     * 办理原地流转（本人状态行）：办理人的待领单行/待审批行 → 终态。
     * 领单（CLAIM）/转交（TRANSFERRED）历史行保留（行为流水）；无状态行时由调用方兜底插终态行。
     */
    @Modifying
    @Query("""
            UPDATE flow_track
            SET track_type = :result,
                assignee = :completedBy,
                next_assignee = NULL,
                action_time = NOW(),
                opinion = :opinion,
                update_user = :operator
            WHERE id = (SELECT t.id FROM (SELECT id FROM flow_track
                                          WHERE instance_id = :instanceId
                                            AND node_instance_id = :nodeInstanceId
                                            AND track_type IN ('PENDING', 'CLAIMED')
                                            AND FIND_IN_SET(:completedBy, assignee) > 0
                                            AND is_deleted = 0
                                          ORDER BY id DESC LIMIT 1) t)
            """)
    Mono<Integer> completeOwnActiveRow(Long instanceId, Long nodeInstanceId,
                                       String completedBy, String result,
                                       String opinion, String operator);

    /**
     * 转交原地流转：转交人的待审批行（CLAIMED）→ TRANSFERRED（assignee=转交人，next=目标人）。
     * 领单（CLAIM）历史行保留；目标人的待处理行（PENDING）由调用方追加。
     */
    @Modifying
    @Query("""
            UPDATE flow_track
            SET track_type = 'TRANSFERRED',
                assignee = :operator,
                next_assignee = :target,
                action_time = NOW(),
                update_user = :operator
            WHERE id = (SELECT t.id FROM (SELECT id FROM flow_track
                                          WHERE instance_id = :instanceId
                                            AND node_instance_id = :nodeInstanceId
                                            AND track_type = 'CLAIMED'
                                            AND FIND_IN_SET(:operator, assignee) > 0
                                            AND is_deleted = 0
                                          ORDER BY id DESC LIMIT 1) t)
            """)
    Mono<Integer> transferTrackRow(Long instanceId, Long nodeInstanceId,
                                   String operator, String target);
}

