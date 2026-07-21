package com.rivers.approval.event;

import java.util.Map;

/**
 * 节点开始执行事件。
 * START / END / USER_TASK / EXCLUSIVE_GATEWAY / PARALLEL_GATEWAY
 *
 */
public record NodeStartedEvent(
        FlowEventMetadata metadata,
        Long nodeInstanceId,
        String nodeId,
        String nodeName,
        String nodeType,
        Map<String, Object> inputVariables
) implements FlowEvent {

    public static NodeStartedEvent of(
            FlowEventMetadata meta,
            Long nodeInstanceId, String nodeId, String nodeName,
            String nodeType, Map<String, Object> inputVariables) {
        var enrichedMeta = new FlowEventMetadata(
                meta.instanceId(), meta.instanceNo(), "NODE_STARTED",
                meta.timestamp(), meta.operatorId(), meta.operatorName(), meta.variables());
        return new NodeStartedEvent(enrichedMeta, nodeInstanceId, nodeId, nodeName,
                nodeType, inputVariables);
    }
}
