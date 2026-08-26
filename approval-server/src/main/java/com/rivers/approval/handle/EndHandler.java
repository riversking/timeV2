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
                // 2. 终止流程实例（CAS：仅 RUNNING 可完成，避免覆盖 TERMINATED）
                .then(ctx.instanceRepo().completeIfRunning(
                        instance.getId(),
                        LocalDateTime.now(ZoneId.systemDefault()),
                        "SYSTEM"))
                .flatMap(rows -> {
                    if (rows <= 0) {
                        log.info("[EndHandler] 实例已非运行中（可能已被终止），跳过完成 instanceId={}",
                                instance.getId());
                        return Mono.empty();
                    }
                    // 3. 发布 InstanceCompletedEvent
                    return Mono.fromRunnable(() -> {
                        var meta = FlowEventMetadata.of(
                                instance.getId(), instance.getInstanceNo(), "INSTANCE_COMPLETED");
                        ctx.eventBus().publish(
                                InstanceCompletedEvent.of(meta, "NORMAL"));
                        log.info("[EndHandler] 流程实例已终止 instanceId={}", instance.getId());
                    });
                });
    }
}
