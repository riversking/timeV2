package com.rivers.approval.event;

import java.util.Map;

/**
 * 流程事件密封接口。
 * 所有事件类型均在此声明，编译器可检查 switch 穷举性。
 */
public sealed interface FlowEvent
        permits InstanceStartedEvent,
        InstanceCompletedEvent,
        NodeStartedEvent,
        NodeCompletedEvent,
        TaskCreatedEvent,
        TaskCompletedEvent {

    /**
     * 公共元数据（所有事件必含）
     */
    FlowEventMetadata metadata();

    // 便捷委托方法 —— 避免每次写 event.metadata().xxx()

    default Long instanceId() {
        return metadata().instanceId();
    }

    default String instanceNo() {
        return metadata().instanceNo();
    }

    default String eventType() {
        return metadata().eventType();
    }

    default String operatorId() {
        return metadata().operatorId();
    }

    default String operatorName() {
        return metadata().operatorName();
    }

    default Map<String, Object> variables() {
        return metadata().variables();
    }
}
