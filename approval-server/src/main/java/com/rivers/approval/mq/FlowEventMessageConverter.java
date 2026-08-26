package com.rivers.approval.mq;

import com.rivers.approval.event.*;
import org.jspecify.annotations.NullMarked;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.amqp.support.converter.MessageConverter;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * FlowEvent ⇄ AMQP Message 转换器（基于 Jackson 3 / tools.jackson）。
 *
 * <p>事件按具体 record 类型序列化，事件类型名写入消息头 x-event-type，
 * 反序列化时经白名单注册表还原具体类型 —— 不依赖多态注解，
 * 也不受 Jackson 默认类型机制（default typing）注入风险影响。
 */
@NullMarked
public class FlowEventMessageConverter implements MessageConverter {

    public static final String TYPE_HEADER = "x-event-type";

    private static final Map<String, Class<? extends FlowEvent>> TYPE_INDEX = Map.of(
            "INSTANCE_STARTED", InstanceStartedEvent.class,
            "INSTANCE_COMPLETED", InstanceCompletedEvent.class,
            "NODE_STARTED", NodeStartedEvent.class,
            "NODE_COMPLETED", NodeCompletedEvent.class,
            "TASK_CREATED", TaskCreatedEvent.class,
            "TASK_COMPLETED", TaskCompletedEvent.class);

    private final ObjectMapper objectMapper;

    public FlowEventMessageConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Message toMessage(Object object, MessageProperties messageProperties) {
        if (!(object instanceof FlowEvent event)) {
            throw new MessageConversionException(
                    "仅支持 FlowEvent 类型: " + object.getClass().getName());
        }
        try {
            byte[] body = objectMapper.writeValueAsBytes(event);
            var props = messageProperties != null ? messageProperties : new MessageProperties();
            props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            props.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            props.setHeader(TYPE_HEADER, event.eventType());
            return new Message(body, props);
        } catch (Exception e) {
            throw new MessageConversionException("事件序列化失败: " + event.eventType(), e);
        }
    }

    @Override
    public Object fromMessage(Message message) {
        var type = message.getMessageProperties().getHeader(TYPE_HEADER);
        if (!(type instanceof String typeName)) {
            throw new MessageConversionException("缺少事件类型头: " + TYPE_HEADER);
        }
        var clazz = TYPE_INDEX.get(typeName);
        if (clazz == null) {
            throw new MessageConversionException("未知事件类型: " + typeName);
        }
        try {
            return objectMapper.readValue(message.getBody(), clazz);
        } catch (Exception e) {
            throw new MessageConversionException("事件反序列化失败: " + typeName, e);
        }
    }
}