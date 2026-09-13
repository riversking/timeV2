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
    @RabbitListener(queues = FlowRabbitConfig.AUDIT_QUEUE)
    public Mono<Void> onAuditEvent(FlowEvent event) {
        return saveRecord(event);
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
     * INSTANCE_STARTED → 发起行；TASK_CREATED → 环节 PENDING 行 + 回填上一行下一处理人；
     * TASK_COMPLETED → PENDING 行原地变终态；其余事件不产生跟踪行。
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

    private Mono<Void> writeCreatedTrack(TaskCreatedEvent e) {
        // 1) 环节首候选人 → 插 PENDING 行；后续候选人 → 追加 assignee
        // 2) 回填上一行行为的 next_assignee
        return trackRepo.findPendingRow(e.instanceId(), e.nodeInstanceId())
                .flatMap(row -> trackRepo.appendAssignee(
                        row.getId(), e.assignee(), e.operatorId()))
                .switchIfEmpty(Mono.defer(() -> trackRepo.save(FlowTrack.builder()
                        .instanceId(e.instanceId())
                        .instanceNo(nvl(e.instanceNo()))
                        .nodeInstanceId(e.nodeInstanceId())
                        .trackType("PENDING")
                        .nodeName(e.taskName())
                        .assignee(nvl(e.assignee()))
                        .taskTime(LocalDateTime.now(ZoneId.systemDefault()))
                        .createUser(e.operatorId())
                        .updateUser(e.operatorId())
                        .build()).thenReturn(0)))
                .then(trackRepo.findPendingRow(e.instanceId(), e.nodeInstanceId()))
                .flatMap(row -> trackRepo.backfillNextAssignee(
                        e.instanceId(), row.getAssignee()))
                .then();
    }

    private Mono<Void> writeCompletedTrack(TaskCompletedEvent e) {
        // PENDING 行原地变终态（保留任务时间）；兜底：无 PENDING 行时直接插终态行
        return trackRepo.completePendingRow(e.instanceId(), e.nodeInstanceId(),
                        e.result(), e.completedBy(), e.comment(), e.operatorId())
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