package com.rivers.approval.audit;

import com.rivers.approval.entity.FlowHistory;
import com.rivers.approval.event.*;
import com.rivers.approval.repository.FlowHistoryRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

/**
 * 审计记录器：订阅引擎全量事件流，写入 flow_history。
 *
 * <p>独立于 FlowExecutor 的推进链路 —— 记录失败只影响审计，不影响流程推进。
 */
@Component
@Slf4j
public class FlowHistoryRecorder {

    private final FlowEventBus eventBus;
    private final FlowHistoryRepository historyRepo;
    private final ObjectMapper objectMapper;

    public FlowHistoryRecorder(FlowEventBus eventBus,
                               FlowHistoryRepository historyRepo,
                               ObjectMapper objectMapper) {
        this.eventBus = eventBus;
        this.historyRepo = historyRepo;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        Flux.merge(
                        eventBus.subscribeShared(InstanceStartedEvent.class),
                        eventBus.subscribeShared(InstanceCompletedEvent.class),
                        eventBus.subscribeShared(NodeStartedEvent.class),
                        eventBus.subscribeShared(NodeCompletedEvent.class),
                        eventBus.subscribeShared(TaskCreatedEvent.class),
                        eventBus.subscribeShared(TaskCompletedEvent.class))
                .flatMap(this::record)
                .subscribe(
                        _ -> {
                        },
                        err -> log.error("[FlowHistoryRecorder] 审计写入异常", err));
        log.info("[FlowHistoryRecorder] 历史记录订阅已就绪");
    }

    private Mono<Void> record(FlowEvent event) {
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