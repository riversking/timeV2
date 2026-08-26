package com.rivers.approval.mq;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

/**
 * RabbitMQ 拓扑与模板配置。
 *
 * <pre>
 * approval.flow.exchange (fanout, durable)
 *   ├── approval.flow.engine  （引擎队列：竞争消费，多副本下每个事件只处理一次）
 *   └── approval.flow.audit   （审计队列）
 *
 * 死信：approval.flow.dlx (direct)
 *   ├── approval.flow.engine.dlq
 *   └── approval.flow.audit.dlq
 * </pre>
 *
 * <p>队列与消息均持久化：服务重启/宕机期间事件不丢失，恢复后继续消费。
 */
@Configuration
@EnableRabbit
@Slf4j
public class FlowRabbitConfig {

    public static final String FLOW_EXCHANGE = "approval.flow.exchange";
    public static final String ENGINE_QUEUE = "approval.flow.engine";
    public static final String AUDIT_QUEUE = "approval.flow.audit";
    public static final String FLOW_DLX = "approval.flow.dlx";
    public static final String ENGINE_DLQ = "approval.flow.engine.dlq";
    public static final String AUDIT_DLQ = "approval.flow.audit.dlq";

    @Bean
    public FanoutExchange flowExchange() {
        return new FanoutExchange(FLOW_EXCHANGE, true, false);
    }

    @Bean
    public Queue engineQueue() {
        return QueueBuilder.durable(ENGINE_QUEUE)
                .deadLetterExchange(FLOW_DLX)
                .deadLetterRoutingKey(ENGINE_DLQ)
                .build();
    }

    @Bean
    public Queue auditQueue() {
        return QueueBuilder.durable(AUDIT_QUEUE)
                .deadLetterExchange(FLOW_DLX)
                .deadLetterRoutingKey(AUDIT_DLQ)
                .build();
    }

    @Bean
    public DirectExchange flowDlx() {
        return new DirectExchange(FLOW_DLX, true, false);
    }

    @Bean
    public Queue engineDlq() {
        return QueueBuilder.durable(ENGINE_DLQ).build();
    }

    @Bean
    public Queue auditDlq() {
        return QueueBuilder.durable(AUDIT_DLQ).build();
    }

    @Bean
    public Binding engineBinding() {
        return BindingBuilder.bind(engineQueue()).to(flowExchange());
    }

    @Bean
    public Binding auditBinding() {
        return BindingBuilder.bind(auditQueue()).to(flowExchange());
    }

    @Bean
    public Binding engineDlqBinding() {
        return BindingBuilder.bind(engineDlq()).to(flowDlx()).with(ENGINE_DLQ);
    }

    @Bean
    public Binding auditDlqBinding() {
        return BindingBuilder.bind(auditDlq()).to(flowDlx()).with(AUDIT_DLQ);
    }

    @Bean
    public FlowEventMessageConverter flowEventMessageConverter(ObjectMapper objectMapper) {
        return new FlowEventMessageConverter(objectMapper);
    }

    /**
     * 自定义 RabbitTemplate：事件转换器 + mandatory + 发布确认/不可路由回调。
     * 定义此 Bean 后 Boot 自动配置让位（@ConditionalOnMissingBean）。
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         FlowEventMessageConverter converter) {
        var template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        template.setMandatory(true);
        template.setConfirmCallback((_, ack, cause) -> {
            if (!ack) {
                log.error("[FlowRabbitConfig] 事件发布未确认 cause={}", cause);
            }
        });
        template.setReturnsCallback(returned -> log.error(
                "[FlowRabbitConfig] 事件不可路由 exchange={}, routingKey={}, replyText={}",
                returned.getExchange(), returned.getRoutingKey(), returned.getReplyText()));
        return template;
    }
}