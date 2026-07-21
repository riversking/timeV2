package com.rivers.approval.event;

import java.util.List;

/**
 * USER_TASK 节点到达时创建任务后发布。
 */
public record TaskCreatedEvent(
        FlowEventMetadata metadata,
        Long taskId,
        String taskNo,
        Long nodeInstanceId,
        String taskName,
        String assignee,
        List<String> candidateUsers
) implements FlowEvent {

    public static TaskCreatedEvent of(
            FlowEventMetadata meta,
            Long taskId, String taskNo, Long nodeInstanceId,
            String taskName, String assignee, List<String> candidateUsers) {
        var enrichedMeta = new FlowEventMetadata(
                meta.instanceId(), meta.instanceNo(), "TASK_CREATED",
                meta.timestamp(), meta.operatorId(), meta.operatorName(), meta.variables());
        return new TaskCreatedEvent(enrichedMeta, taskId, taskNo, nodeInstanceId,
                taskName, assignee, candidateUsers);
    }
}
