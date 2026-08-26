package com.rivers.approval.audit;

import com.rivers.approval.entity.FlowHistory;
import com.rivers.approval.event.FlowEvent;
import com.rivers.approval.mq.FlowRabbitConfig;
import com.rivers.approval.repository.FlowHistoryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

/**
 * 审计记录器：消费 audit 队列的全量事件，写入 flow_history。
 *
 * <p>独立于 FlowExecutor 的推进链路 —— 记录失败只影响审计，不影响流程推进。
 * 写入失败时降级为仅记错误日志（审计尽力而为，避免阻塞队列）。
 */
@Component
@Slf4j
public class FlowHistoryRecorder {

    private final FlowHistoryRepository historyRepo;
    private final ObjectMapper objectMapper;

    public FlowHistoryRecorder(FlowHistoryRepository historyRepo,
                               ObjectMapper objectMapper) {
        this.historyRepo = historyRepo;
        this.objectMapper = objectMapper;
    }

    /**
     * audit 队列监听：fanout 交换机会把全部 6 类事件路由到该队列。
     */
    @RabbitListener(queues = FlowRabbitConfig.AUDIT_QUEUE)
    public Mono<Void> onAuditEvent(FlowEvent event) {
        return saveRecord(event);
    }

    private Mono<Void> saveRecord(FlowEvent event) {
        var history = FlowHistory.builder()
                .instanceId(event.instanceId())
                .eventType(event.eventType())
                .operatorId(event.operatorId())
                .operatorName(event.operatorName())
                .detail(toJson(event))
                .createUser(event.operatorId())
                .updateUser(event.operatorId())
                .build();
        return historyRepo.save(history)
                .doOnError(err -> log.error(
                        "[FlowHistoryRecorder] 历史写入失败 instanceId={}, type={}",
                        event.instanceId(), event.eventType(), err))
                .onErrorResume(_ -> Mono.empty())
                .then();
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }
}