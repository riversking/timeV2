package com.rivers.approval.mq;

import com.rivers.approval.engine.FlowExecutor;
import com.rivers.approval.event.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 引擎队列消费者：将 RabbitMQ 消息桥接进 FlowExecutor 的推进方法。
 *
 * <p>返回 Mono&lt;Void&gt;：容器等待响应式链完成后才 ACK；
 * 出错时按 listener 配置重试（spring.rabbitmq.listener.simple.retry），
 * 重试耗尽后经 DLX 进入死信队列，毒消息不会无限循环。
 *
 * <p>多副本部署时同一队列为竞争消费：每个事件只被一个副本的引擎处理一次。
 * 单消费者线程（concurrency=1）保证同一实例的事件按 FIFO 顺序处理。
 */
@Component
@Slf4j
public class FlowEventConsumer {

    private final FlowExecutor executor;

    public FlowEventConsumer(FlowExecutor executor) {
        this.executor = executor;
    }

    @RabbitListener(queues = FlowRabbitConfig.ENGINE_QUEUE)
    public Mono<Void> onEngineEvent(FlowEvent event) {
        log.debug("[FlowEventConsumer] 收到引擎事件 type={}, instanceId={}",
                event.eventType(), event.instanceId());
        // sealed 接口 + 模式匹配：编译器保证穷举
        return switch (event) {
            case InstanceStartedEvent e -> executor.onInstanceStarted(e);
            case NodeCompletedEvent e -> executor.onNodeCompleted(e);
            case TaskCompletedEvent e -> executor.onTaskCompleted(e);
            case InstanceCompletedEvent _, NodeStartedEvent _, TaskCreatedEvent _ -> Mono.empty();
        };
    }

    /**
     * 死信监听：仅记录告警，便于人工介入排查毒消息。
     */
    @RabbitListener(queues = {FlowRabbitConfig.ENGINE_DLQ, FlowRabbitConfig.AUDIT_DLQ})
    public void onDeadLetter(Message message) {
        log.error("[FlowEventConsumer] 死信消息 queue={}, headers={}, body={}",
                message.getMessageProperties().getConsumerQueue(),
                message.getMessageProperties().getHeaders(),
                new String(message.getBody()));
    }
}