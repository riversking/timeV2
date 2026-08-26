package com.rivers.approval.event;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 流程引擎事件总线。
 *
 * <p>基于 {@link Sinks.Many} 的响应式发布/订阅中枢。
 * 每个事件类型维护独立 Sink，订阅者按类型获取热流。
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 发布
 * eventBus.publish(InstanceStartedEvent.of(meta, defId, defKey, initiator));
 *
 * // 订阅（在 FlowExecutor 等引擎组件中）
 * eventBus.subscribe(NodeCompletedEvent.class)
 *     .flatMap(this::advanceToNext)
 *     .subscribe();
 *
 * // 穷举模式匹配（Java 21 新特性）
 * String desc = switch (event) {
 *     case InstanceStartedEvent  e -> "流程发起: " + e.initiator();
 *     case InstanceCompletedEvent e -> "流程结束: " + e.reason();
 *     case NodeStartedEvent      e -> "节点开始: " + e.nodeName();
 *     case NodeCompletedEvent    e -> "节点完成: " + e.nodeName();
 *     case TaskCreatedEvent      e -> "任务创建: " + e.taskName();
 *     case TaskCompletedEvent    e -> "任务完成: " + e.result();
 * };
 * }</pre>
 */
@Component
@Slf4j
public class FlowEventBus {


    private static final int DEFAULT_BUFFER_SIZE = 256;
    private static final int MAX_EMIT_ATTEMPTS = 3;

    private final ConcurrentMap<Class<? extends FlowEvent>, Sinks.Many<FlowEvent>> sinkMap =
            new ConcurrentHashMap<>();

    // ==================== 发布 ====================

    /**
     * 发布事件。按事件运行时类型路由到对应 Sink。
     */
    public void publish(FlowEvent event) {
        if (event == null) {
            log.warn("[FlowEventBus] 忽略 null 事件");
            return;
        }
        var eventClass = (Class<? extends FlowEvent>) event.getClass();
        var sink = sinkMap.computeIfAbsent(eventClass,
                _ -> newSink());
        emitWithRetry(sink, event);
    }

    /**
     * 带重试的事件发射。
     * FAIL_NON_SERIALIZED 为并发竞争瞬时态，重试即可；
     * FAIL_OVERFLOW 在无界缓冲下不应出现；
     * FAIL_CANCELLED/FAIL_TERMINATED 仅发生在停机清理阶段。
     */
    private void emitWithRetry(Sinks.Many<FlowEvent> sink, FlowEvent event) {
        for (int attempt = 1; attempt <= MAX_EMIT_ATTEMPTS; attempt++) {
            switch (sink.tryEmitNext(event)) {
                case OK -> {
                    log.debug("[FlowEventBus] ✓ {} instanceId={}", event.eventType(), event.instanceId());
                    return;
                }
                case FAIL_NON_SERIALIZED -> log.debug(
                        "[FlowEventBus] 并发竞争，重试 {}/{}，{}/{}",
                        attempt, MAX_EMIT_ATTEMPTS, event.eventType(), event.instanceId());
                case FAIL_OVERFLOW -> {
                    log.error("[FlowEventBus] 缓冲区溢出（无界缓冲下不应出现），{}/{}",
                            event.eventType(), event.instanceId());
                    return;
                }
                case FAIL_CANCELLED, FAIL_TERMINATED -> {
                    log.error("[FlowEventBus] Sink 已终止，事件丢弃 {}/{}",
                            event.eventType(), event.instanceId());
                    return;
                }
            }
        }
        log.error("[FlowEventBus] 重试 {} 次后仍失败，事件丢失 {}/{}，请检查引擎订阅是否存活",
                MAX_EMIT_ATTEMPTS, event.eventType(), event.instanceId());
    }

    // ==================== 订阅 ====================

    /**
     * 获取指定事件类型的热流。
     */
    public <T extends FlowEvent> Flux<T> subscribe(Class<T> eventClass) {
        var sink = sinkMap.computeIfAbsent(eventClass, _ -> newSink());
        return sink.asFlux().cast(eventClass);
    }

    /**
     * 获取指定事件类型的共享流（多个下游共享同一上游订阅）。
     */
    public <T extends FlowEvent> Flux<T> subscribeShared(Class<T> eventClass) {
        var sink = sinkMap.computeIfAbsent(eventClass, _ -> newSink());
        return sink.asFlux().cast(eventClass).share();
    }

    /**
     * 获取所有事件的合并流（审计/监控用）。
     */
    public Flux<FlowEvent> subscribeAll() {
        var streams = sinkMap.values().stream()
                .map(Sinks.Many::asFlux)
                .toList();
        return streams.isEmpty() ? Flux.empty() : Flux.merge(streams);
    }

    // ==================== 辅助 ====================

    private static Sinks.Many<FlowEvent> newSink() {
        // 无界缓冲：事件丢失即流程卡死，正确性优先于内存占用；
        // 生产环境建议进一步替换为 RabbitMQ（spring-cloud-starter-bus-amqp 已在依赖中）
        return Sinks.many()
                .multicast()
                .onBackpressureBuffer();
    }

    public int registeredEventTypes() {
        return sinkMap.size();
    }

    @PreDestroy
    public void destroy() {
        log.info("[FlowEventBus] 清理中，已注册 {} 种事件类型", sinkMap.size());
        sinkMap.values().forEach(Sinks.Many::tryEmitComplete);
        sinkMap.clear();
        log.info("[FlowEventBus] 清理完毕");
    }
}
