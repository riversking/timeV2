package com.rivers.approval.event;

/**
 * 任务完成事件，引擎订阅后触发对应节点完成。
 * APPROVED / REJECTED
 */
public record TaskCompletedEvent(
        FlowEventMetadata metadata,
        Long taskId,
        String taskNo,
        Long nodeInstanceId,
        String result,
        String comment,
        String completedBy
) implements FlowEvent {

    public static TaskCompletedEvent of(
            FlowEventMetadata meta,
            Long taskId, String taskNo, Long nodeInstanceId,
            String result, String comment, String completedBy) {
        var enrichedMeta = new FlowEventMetadata(
                meta.instanceId(), meta.instanceNo(), "TASK_COMPLETED",
                meta.timestamp(), meta.operatorId(), meta.operatorName(), meta.variables());
        return new TaskCompletedEvent(enrichedMeta, taskId, taskNo, nodeInstanceId,
                result, comment, completedBy);
    }
}
