package com.rivers.approval.audit;

import com.rivers.approval.entity.FlowHistory;
import com.rivers.approval.event.FlowEvent;
import com.rivers.approval.event.InstanceCompletedEvent;
import com.rivers.approval.event.InstanceStartedEvent;
import com.rivers.approval.event.NodeCompletedEvent;
import com.rivers.approval.event.NodeStartedEvent;
import com.rivers.approval.event.TaskCompletedEvent;
import com.rivers.approval.event.TaskCreatedEvent;
import com.rivers.approval.mq.FlowRabbitConfig;
import com.rivers.approval.repository.FlowHistoryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 审计记录器：消费 audit 队列的全量事件，写入 flow_history。
 *
 * <p>独立于 FlowExecutor 的推进链路 —— 记录失败只影响审计，不影响流程推进。
 * 写入失败时降级为仅记错误日志（审计尽力而为，避免阻塞队列）。
 *
 * <p>flow_history 结构化拆分：事件按类型映射到节点/任务/办理人/结果/意见/状态变更列，
 * 人读摘要放入 remark，不再使用 JSON detail。
 */
@Component
@Slf4j
public class FlowHistoryRecorder {

    private final FlowHistoryRepository historyRepo;

    public FlowHistoryRecorder(FlowHistoryRepository historyRepo) {
        this.historyRepo = historyRepo;
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

    private static String nvl(String s) {
        return s == null ? "" : s;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}