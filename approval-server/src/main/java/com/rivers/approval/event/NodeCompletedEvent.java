package com.rivers.approval.event;

import java.util.Map;

/**
 * 节点完成事件。
 * 引擎订阅此事件以推进到后续节点。
 * 排他网关目标节点ID，非网关节点为 null
 */
public record NodeCompletedEvent(
        FlowEventMetadata metadata,
        Long nodeInstanceId,
        String nodeId,
        String nodeName,
        String nodeType,
        Map<String, Object> outputVariables,
        String targetNodeId
) implements FlowEvent {

    public static NodeCompletedEvent of(
            FlowEventMetadata meta,
            Long nodeInstanceId, String nodeId, String nodeName,
            String nodeType, Map<String, Object> outputVariables,
            String targetNodeId) {
        var enrichedMeta = new FlowEventMetadata(
                meta.instanceId(), meta.instanceNo(), "NODE_COMPLETED",
                meta.timestamp(), meta.operatorId(), meta.operatorName(), meta.variables());
        return new NodeCompletedEvent(enrichedMeta, nodeInstanceId, nodeId, nodeName,
                nodeType, outputVariables, targetNodeId);
    }
}
