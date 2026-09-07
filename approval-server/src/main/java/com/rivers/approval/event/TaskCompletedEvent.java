package com.rivers.approval.event;

/**
 * 任务完成事件。
 * <p>
 * advanceNode = true  — 节点可推进（单人模式 / 或签 / 会签已集齐 / 串签最后一人），
 *                       引擎标记节点完成并继续流转；
 * advanceNode = false — 仅办理记录（会签/串签尚未集齐），引擎不推进节点，
 *                       审计队列仍会落库本次办理历史。
 * 旧消息无该字段（null）时按 true 处理，兼容存量队列消息。
 */
public record TaskCompletedEvent(
        FlowEventMetadata metadata,
        Long taskId,
        String taskNo,
        Long nodeInstanceId,
        String result,
        String comment,
        String completedBy,
        Boolean advanceNode
) implements FlowEvent {

    public static TaskCompletedEvent of(
            FlowEventMetadata meta,
            Long taskId, String taskNo, Long nodeInstanceId,
            String result, String comment, String completedBy,
            boolean advanceNode) {
        var enrichedMeta = new FlowEventMetadata(
                meta.instanceId(), meta.instanceNo(), "TASK_COMPLETED",
                meta.timestamp(), meta.operatorId(), meta.operatorName(), meta.variables());
        return new TaskCompletedEvent(enrichedMeta, taskId, taskNo, nodeInstanceId,
                result, comment, completedBy, advanceNode);
    }
}