package com.rivers.approval.event;

/**
 * 流程实例发起事件。
 */
public record InstanceStartedEvent(
        FlowEventMetadata metadata,
        Long definitionId,
        String definitionKey,
        String initiator
) implements FlowEvent {

    public InstanceStartedEvent {
        // record 紧凑构造器自动执行，无需显式赋值
    }

    /**
     * 便捷工厂
     */
    public static InstanceStartedEvent of(
            FlowEventMetadata meta,
            Long definitionId,
            String definitionKey,
            String initiator) {
        var enrichedMeta = new FlowEventMetadata(
                meta.instanceId(), meta.instanceNo(), "INSTANCE_STARTED",
                meta.timestamp(), meta.operatorId(), meta.operatorName(), meta.variables());
        return new InstanceStartedEvent(enrichedMeta, definitionId, definitionKey, initiator);
    }
}
