package com.rivers.approval.event;

/**
 * 流程实例完成事件。
 * 结束原因：NORMAL / TERMINATED
 */
public record InstanceCompletedEvent(
        FlowEventMetadata metadata,
        String reason
) implements FlowEvent {

    public InstanceCompletedEvent {
        // NORMAL 为默认值
    }

    public static InstanceCompletedEvent of(FlowEventMetadata meta, String reason) {
        var enrichedMeta = new FlowEventMetadata(
                meta.instanceId(), meta.instanceNo(), "INSTANCE_COMPLETED",
                meta.timestamp(), meta.operatorId(), meta.operatorName(), meta.variables());
        return new InstanceCompletedEvent(enrichedMeta, reason != null ? reason : "NORMAL");
    }
}
