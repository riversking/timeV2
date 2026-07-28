package com.rivers.approval.handle;

import com.rivers.approval.event.FlowEventMetadata;
import com.rivers.approval.event.NodeCompletedEvent;
import com.rivers.approval.model.NodeContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Component
@Slf4j
public class StartHandler implements NodeHandler {

    @Override
    public String supportedType() {
        return "START";
    }

    @Override
    public Mono<Void> handle(NodeContext ctx) {
        var nodeInstance = ctx.currentNode();
        var instance = ctx.instance();
        log.info("[StartHandler] 流程开始 instanceId={}, definitionKey={}", instance.getId(), instance.getDefinitionKey());
        return ctx.nodeRepo().updateNodeStatus(
                        nodeInstance.getId(),
                        "COMPLETED",
                        null,                         // 无输出变量
                        LocalDateTime.now(ZoneId.systemDefault()),
                        "SYSTEM"
                )
                .then(Mono.fromRunnable(() -> {
                    var meta = FlowEventMetadata.of(
                            instance.getId(),
                            instance.getInstanceNo(),
                            "NODE_COMPLETED");
                    var event = NodeCompletedEvent.of(
                            meta,
                            nodeInstance.getId(),
                            nodeInstance.getNodeId(),
                            nodeInstance.getNodeName(),
                            nodeInstance.getNodeType(),
                            null,     // outputVariables
                            null      // targetNodeId（Start 不选分支）
                    );
                    ctx.eventBus().publish(event);
                }));
    }
}
