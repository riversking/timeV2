package com.rivers.approval.handle;

import com.rivers.approval.event.FlowEventMetadata;
import com.rivers.approval.event.InstanceCompletedEvent;
import com.rivers.approval.model.NodeContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * END 节点处理器。
 * 标记当前节点完成 → 终止流程实例 → 发布 InstanceCompletedEvent。
 */
@Slf4j
@Component
public class EndHandler implements NodeHandler {

    @Override
    public String supportedType() {
        return "END";
    }

    @Override
    public Mono<Void> handle(NodeContext ctx) {
        var nodeInstance = ctx.currentNode();
        var instance = ctx.instance();
        log.info("[EndHandler] 流程结束 instanceId={}, instanceNo={}", instance.getId(), instance.getInstanceNo());
        // 1. 标记节点完成
        return ctx.nodeRepo().updateNodeStatus(
                        nodeInstance.getId(),
                        "COMPLETED",
                        null,
                        LocalDateTime.now(ZoneId.systemDefault()),
                        "SYSTEM"
                )
                // 2. 终止流程实例
                .then(ctx.instanceRepo().updateStatus(
                        instance.getId(),
                        "COMPLETED",
                        LocalDateTime.now(ZoneId.systemDefault()),
                        "SYSTEM"))
                .then(Mono.fromRunnable(() -> {
                    // 3. 发布 InstanceCompletedEvent
                    var meta = FlowEventMetadata.of(
                            instance.getId(), instance.getInstanceNo(), "INSTANCE_COMPLETED");
                    ctx.eventBus().publish(
                            InstanceCompletedEvent.of(meta, "NORMAL"));
                    log.info("[EndHandler] 流程实例已终止 instanceId={}", instance.getId());
                }));
    }
}
