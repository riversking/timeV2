package com.rivers.approval.audit;

import com.rivers.approval.entity.FlowHistory;
import com.rivers.approval.entity.FlowTrack;
import com.rivers.approval.event.*;
import com.rivers.approval.mq.FlowRabbitConfig;
import com.rivers.approval.repository.FlowHistoryRepository;
import com.rivers.approval.repository.FlowTrackRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 审计记录器：消费 audit 队列的全量事件。
 * <ul>
 *   <li>flow_history —— 审计流水（6 类事件全量落库）</li>
 *   <li>flow_track —— 跟踪链展示表（行为发生时插行/原地更新，查询零聚合）</li>
 * </ul>
 * <p>独立于 FlowExecutor 的推进链路 —— 记录失败只影响审计/展示，不影响流程推进。
 * 写入失败时降级为仅记错误日志（审计尽力而为，避免阻塞队列）。
 */
@Component
@Slf4j
public class FlowHistoryRecorder {

    private final FlowHistoryRepository historyRepo;
    private final FlowTrackRepository trackRepo;

    public FlowHistoryRecorder(FlowHistoryRepository historyRepo,
                               FlowTrackRepository trackRepo) {
        this.historyRepo = historyRepo;
        this.trackRepo = trackRepo;
    }

    /**
     * audit 队列监听：fanout 交换机会把全部 6 类事件路由到该队列。
     */
    @RabbitListener(queues = FlowRabbitConfig.AUDIT_QUEUE, concurrency = "1")
    public void onAuditEvent(FlowEvent event) {
        // 阻塞至落库链完成再取下一条：单消费者严格串行，避免多任务事件交叉写跟踪行
        saveRecord(event).block();
    }

    private Mono<Void> saveRecord(FlowEvent event) {
        return historyRepo.save(mapToHistory(event))
                .then(writeTrack(event))
                .doOnError(err -> log.error(
                        "[FlowHistoryRecorder] 历史写入失败 instanceId={}, type={}",
                        event.instanceId(), event.eventType(), err))
                .onErrorResume(_ -> Mono.empty())
                .then();
    }

    /**
     * 结构化拆列：公共元数据统一填充，业务列按事件类型 switch 填充。
     * FlowEvent 是 sealed 接口，编译器保证穷举。
     */
    private FlowHistory mapToHistory(FlowEvent event) {
        var builder = FlowHistory.builder()
                .instanceId(event.instanceId())
                .instanceNo(nvl(event.instanceNo()))
                .eventType(event.eventType())
                .operatorId(event.operatorId())
                .operatorName(event.operatorName())
                .createUser(event.operatorId())
                .updateUser(event.operatorId());
        switch (event) {
            case InstanceStartedEvent e -> builder
                    .remark("发起流程 definitionKey=" + nvl(e.definitionKey())
                            + " 发起人:" + nvl(e.initiator()));
            case InstanceCompletedEvent e -> builder
                    .toStatus(e.reason())
                    .remark("流程结束 原因:" + nvl(e.reason()));
            case NodeStartedEvent e -> builder
                    .nodeInstanceId(e.nodeInstanceId())
                    .nodeId(e.nodeId())
                    .nodeName(e.nodeName())
                    .nodeType(e.nodeType())
                    .remark("节点开始 [" + nvl(e.nodeName()) + "]");
            case NodeCompletedEvent e -> builder
                    .nodeInstanceId(e.nodeInstanceId())
                    .nodeId(e.nodeId())
                    .nodeName(e.nodeName())
                    .nodeType(e.nodeType())
                    .remark("节点完成 [" + nvl(e.nodeName()) + "]"
                            + (isBlank(e.targetNodeId()) ? "" : " 下一节点:" + e.targetNodeId()));
            case TaskCreatedEvent e -> builder
                    .taskId(e.taskId())
                    .taskNo(e.taskNo())
                    .nodeInstanceId(e.nodeInstanceId())
                    .taskName(e.taskName())
                    .assignee(e.assignee())
                    .toStatus("PENDING")
                    .remark("任务创建 [" + nvl(e.taskName()) + "] 办理人:" + nvl(e.assignee())
                            + (e.candidateUsers() == null || e.candidateUsers().isEmpty()
                            ? "" : " 候选人:" + String.join(",", e.candidateUsers())));
            case TaskCompletedEvent e -> builder
                    .taskId(e.taskId())
                    .taskNo(e.taskNo())
                    .nodeInstanceId(e.nodeInstanceId())
                    .taskName(e.taskName())
                    .assignee(e.completedBy())
                    .result(e.result())
                    .opinion(e.comment())
                    .toStatus("COMPLETED")
                    .remark("任务完成 [" + nvl(e.taskName()) + "] 结果:" + nvl(e.result())
                            + (isBlank(e.comment()) ? "" : " 意见:" + e.comment()));
        }
        return builder.build();
    }

    // ==================== 跟踪链落库（入库即展示） ====================

    /**
     * 行为发生即写 flow_track 一行：
     * INSTANCE_STARTED → 发起行；TASK_CREATED → 每候选人一条 PENDING 行 + 回填上一行下一处理人；
     * TASK_COMPLETED → 活跃行原地变终态；其余事件不产生跟踪行。
     */
    private Mono<Void> writeTrack(FlowEvent event) {
        return switch (event) {
            case InstanceStartedEvent e -> writeStartedTrack(e);
            case TaskCreatedEvent e -> writeCreatedTrack(e);
            case TaskCompletedEvent e -> writeCompletedTrack(e);
            default -> Mono.empty();
        };
    }

    private Mono<Void> writeStartedTrack(InstanceStartedEvent e) {
        return trackRepo.save(FlowTrack.builder()
                        .instanceId(e.instanceId())
                        .instanceNo(nvl(e.instanceNo()))
                        .nodeInstanceId(0L)
                        .trackType("STARTED")
                        .nodeName("发起申请")
                        .assignee(nvl(e.initiator()))
                        .taskTime(LocalDateTime.now(ZoneId.systemDefault()))
                        .createUser(e.operatorId())
                        .updateUser(e.operatorId())
                        .build())
                .then();
    }

    /**
     * 待领单行：每个候选人一条独立 PENDING 行（不合并）。
     * 幂等：本人已有行则跳过插入；环节已认领/办结时，迟到的候选事件不再补插。
     * 最后回填上一环节行 next_assignee = 本环节全部候选聚合（"下一处理人"）。
     */
    private Mono<Void> writeCreatedTrack(TaskCreatedEvent e) {
        return trackRepo.findPendingRowByAssignee(e.instanceId(), e.nodeInstanceId(), e.assignee())
                .map(row -> 0)
                .switchIfEmpty(Mono.defer(() -> trackRepo.countSettledRowsOfNode(
                                e.instanceId(), e.nodeInstanceId())
                        .filter(count -> count == 0)
                        .flatMap(_ -> trackRepo.save(FlowTrack.builder()
                                        .instanceId(e.instanceId())
                                        .instanceNo(nvl(e.instanceNo()))
                                        .nodeInstanceId(e.nodeInstanceId())
                                        .trackType("PENDING")
                                        .nodeName(e.taskName())
                                        .assignee(nvl(e.assignee()))
                                        .taskTime(LocalDateTime.now(ZoneId.systemDefault()))
                                        .createUser(e.operatorId())
                                        .updateUser(e.operatorId())
                                        .build())
                                .thenReturn(0))))
                .flatMap(_ -> trackRepo.firstRowIdOfNode(e.instanceId(), e.nodeInstanceId())
                        .flatMap(firstId -> trackRepo.pendingAssigneesOfNode(
                                        e.instanceId(), e.nodeInstanceId())
                                .defaultIfEmpty("")
                                .flatMap(joined -> joined.isEmpty()
                                        ? Mono.<Integer>empty()
                                        : trackRepo.backfillNextAssignee(
                                                e.instanceId(), firstId, joined))))
                .then();
    }

    /**
     * 办理行（行为流水）：办理人的状态行（待领单 PENDING/待审批 CLAIMED）原地流转为终态（保留任务时间）；
     * 无本人状态行时兑底插终态行——领单 CLAIM/转交 TRANSFERRED 历史行保留（如串签后续顺位、接单人场景）。
     */
    private Mono<Void> writeCompletedTrack(TaskCompletedEvent e) {
        return trackRepo.completeOwnActiveRow(e.instanceId(), e.nodeInstanceId(),
                        e.completedBy(), e.result(), e.comment(), e.operatorId())
                .filter(rows -> rows > 0)
                .switchIfEmpty(Mono.defer(() -> trackRepo.save(FlowTrack.builder()
                        .instanceId(e.instanceId())
                        .instanceNo(nvl(e.instanceNo()))
                        .nodeInstanceId(e.nodeInstanceId())
                        .trackType(e.result())
                        .nodeName(e.taskName())
                        .assignee(e.completedBy())
                        .taskTime(LocalDateTime.now(ZoneId.systemDefault()))
                        .actionTime(LocalDateTime.now(ZoneId.systemDefault()))
                        .opinion(e.comment())
                        .createUser(e.operatorId())
                        .updateUser(e.operatorId())
                        .build()).thenReturn(0)))
                .then();
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}