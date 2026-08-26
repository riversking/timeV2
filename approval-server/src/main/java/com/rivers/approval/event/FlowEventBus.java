package com.rivers.approval.event;

import com.rivers.approval.mq.FlowRabbitConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * 流程事件总线（RabbitMQ 生产端门面）。
 *
 * <p>发布侧语义不变：所有 Handler/Service 继续调用 {@link #publish(FlowEvent)}。
 * 事件经 fanout 交换机 {@code approval.flow.exchange} 广播到两条持久化队列：
 * <ul>
 *   <li>{@code approval.flow.engine} — {@code FlowEventConsumer} 消费，驱动 FlowExecutor 推进</li>
 *   <li>{@code approval.flow.audit}  — {@code FlowHistoryRecorder} 消费，写入 flow_history</li>
 * </ul>
 *
 * <p>可靠性：队列持久化 + 消息 PERSISTENT + publisher confirm/return 回调；
 * 服务重启/宕机不再丢失事件。投递失败仅记错误日志，
 * 彻底方案（DB 提交与投递原子性）见 Transactional Outbox 模式说明。
 */
@Component
@Slf4j
public class FlowEventBus {

    private final RabbitTemplate rabbitTemplate;

    public FlowEventBus(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * 发布事件。fanout 交换机忽略 routing key。
     */
    public void publish(FlowEvent event) {
        if (event == null) {
            log.warn("[FlowEventBus] 忽略 null 事件");
            return;
        }
        try {
            rabbitTemplate.convertAndSend(FlowRabbitConfig.FLOW_EXCHANGE, "", event);
        } catch (Exception e) {
            log.error("[FlowEventBus] 事件投递异常 type={}, instanceId={}",
                    event.eventType(), event.instanceId(), e);
        }
    }
}