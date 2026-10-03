package com.rivers.approval.handle;

import com.rivers.approval.event.FlowEventMetadata;
import com.rivers.approval.event.InstanceCompletedEvent;
import com.rivers.approval.model.NodeContext;
import com.rivers.approval.repository.FlowTaskDoneRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * END 节点处理器。
 * 标记当前节点完成 → 终止流程实例 → 级联清理其余分支活跃任务 → 发布 InstanceCompletedEvent。
 */
@Slf4j
@Component
public class EndHandler implements NodeHandler {

    private final FlowTaskDoneRepository taskDoneRepo;

    public EndHandler(FlowTaskDoneRepository taskDoneRepo) {
        this.taskDoneRepo = taskDoneRepo;
    }

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
                        "",
                        LocalDateTime.now(ZoneId.systemDefault()),
                        "SYSTEM"
                )
                // 2. 终止流程实例（CAS：仅 RUNNING 可完成，避免覆盖 TERMINATED）
                .then(ctx.instanceRepo().completeIfRunning(
                        instance.getId(),
                        LocalDateTime.now(ZoneId.systemDefault()),
                        "SYSTEM"))
                .flatMap(rows -> {
                    // 3. 级联清理其余分支的活跃任务（冻结 → 归档已办 → 物理删除；幂等）
                    //    覆盖“双退回结束”场景：另一分支的修改任务等不得残留悬空
                    var cleanup = ctx.taskRepo().cancelActiveByInstanceId(instance.getId(), "SYSTEM")
                            .then(taskDoneRepo.archiveCancelledByInstanceId(instance.getId(), "SYSTEM"))
                            .then(ctx.taskRepo().deleteCancelledByInstanceId(instance.getId()));
                    if (rows <= 0) {
                        log.info("[EndHandler] 实例已非运行中（可能已被终止），补清理后跳过完成 instanceId={}",
                                instance.getId());
                        return cleanup.then();
                    }
                    // 4. 发布 InstanceCompletedEvent
                    return cleanup.then(Mono.fromRunnable(() -> {
                        var meta = FlowEventMetadata.of(
                                instance.getId(), instance.getInstanceNo(), "INSTANCE_COMPLETED");
                        ctx.eventBus().publish(
                                InstanceCompletedEvent.of(meta, "NORMAL"));
                        log.info("[EndHandler] 流程实例已终止 instanceId={}", instance.getId());
                    }));
                });
    }
}
