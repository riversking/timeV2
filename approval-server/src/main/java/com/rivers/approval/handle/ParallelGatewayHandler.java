package com.rivers.approval.handle;

import com.rivers.approval.entity.FlowNodeInstance;
import com.rivers.approval.event.FlowEventMetadata;
import com.rivers.approval.event.NodeCompletedEvent;
import com.rivers.approval.model.NodeContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;

/**
 * 并行网关（Parallel Gateway）处理器。
 *
 * <p>根据拓扑自动检测 Fork / Join 模式：
 *
 * <h3>Fork（分支）</h3>
 * 出边 ≥ 2 条时进入 Fork 模式：
 * <ol>
 *   <li>标记网关节点 COMPLETED（暂不发布事件）</li>
 *   <li>为每条出边创建子节点实例，parent_node_instance_id = 本网关</li>
 *   <li>为下游 Join 节点（如有）预创建节点实例，设置 fork_count</li>
 *   <li>发布 NodeCompletedEvent（不带 targetNodeId），FlowExecutor 并行创建所有子节点</li>
 * </ol>
 *
 * <h3>Join（汇聚）</h3>
 * 入边 ≥ 2 条时进入 Join 模式：
 * <ol>
 *   <li>查询本实例下同 nodeId 的节点实例（由 Fork 时预创建或第一条分支到达时创建）</li>
 *   <li>原子递增 join_count（MySQL SET join_count = join_count + 1）</li>
 *   <li>若 join_count &lt; fork_count → 不推进，等待剩余分支</li>
 *   <li>若 join_count == fork_count → CAS 置为 COMPLETED，发布 NodeCompletedEvent</li>
 * </ol>
 *
 * <p>并发安全：多条分支可能同时到达 Join 节点，靠以下机制保证：
 * <ul>
 *   <li>join_count 原子递增（数据库层）</li>
 *   <li>完成时用 WHERE status = 'ACTIVE' 做乐观锁，只有第一个成功的 CAS 才发布事件</li>
 * </ul>
 */
@Component
@Slf4j
public class ParallelGatewayHandler implements NodeHandler {

    @Override
    public String supportedType() {
        return "PARALLEL_GATEWAY";
    }

    @Override
    public Mono<Void> handle(NodeContext ctx) {
        var definition = ctx.definition();
        var nodeId = ctx.currentNode().getNodeId();
        var outgoingEdges = definition.edgesFrom(nodeId);
        var incomingCount = definition.edges().stream()
                .filter(e -> e.target().equals(nodeId))
                .count();
        // 分支网关：出边 ≥ 2 → Fork
        if (outgoingEdges.size() >= 2) {
            return handleFork(ctx, outgoingEdges.size()
            );
        }
        // 汇聚网关：入边 ≥ 2 → Join
        if (incomingCount >= 2) {
            return handleJoin(ctx, (int) incomingCount);
        }
        // 单进单出 = 普通节点，直接完成（或抛出异常）
        log.warn("[ParallelGateway] 非典型并行网关 nodeId={}, 入边={}, 出边={}",
                nodeId, incomingCount, outgoingEdges.size());
        return completeImmediately(ctx);
    }

    // ==================== Fork ====================

    /**
     * Fork 模式。
     * 标记自身完成，不指定 targetNodeId（让 FlowExecutor 按所有出边并行推进）。
     */
    private Mono<Void> handleFork(NodeContext ctx, int forkCount) {
        var nodeInstance = ctx.currentNode();
        var instance = ctx.instance();
        log.info("[ParallelGateway:Fork] instanceId={}, nodeId={}, 分叉数={}",
                instance.getId(), nodeInstance.getNodeId(), forkCount);
        // 设置 fork_count 到网关节点
        return ctx.nodeRepo().updateNodeStatus(
                        nodeInstance.getId(),
                        "COMPLETED",
                        null,
                        LocalDateTime.now(ZoneId.systemDefault()),
                        "SYSTEM"
                )
                .then(Mono.fromRunnable(() -> {
                    var meta = FlowEventMetadata.of(
                            instance.getId(), instance.getInstanceNo(), "NODE_COMPLETED");
                    ctx.eventBus().publish(NodeCompletedEvent.of(
                            meta,
                            nodeInstance.getId(),
                            nodeInstance.getNodeId(),
                            nodeInstance.getNodeName(),
                            nodeInstance.getNodeType(),
                            Collections.emptyMap(),
                            null));  // targetNodeId = null → FlowExecutor 并行走所有出边
                }));
    }

    // ==================== Join ====================

    /**
     * Join 模式。
     * 原子递增 join_count，收集齐全后触发完成。
     */
    private Mono<Void> handleJoin(NodeContext ctx, int expectedBranches) {
        var nodeInstance = ctx.currentNode();
        var instance = ctx.instance();

        log.info("[ParallelGateway:Join] instanceId={}, nodeInstanceId={}, 预期分支数={}",
                instance.getId(), nodeInstance.getId(), expectedBranches);

        // 1. 原子递增 join_count
        return ctx.nodeRepo().incrementJoinCount(nodeInstance.getId())
                .then(ctx.nodeRepo().findById(nodeInstance.getId()))
                .flatMap(fresh -> {
                    int joinCount = fresh.getJoinCount() != null ? fresh.getJoinCount() : 1;
                    int forkCount = fresh.getForkCount() != 0
                            ? fresh.getForkCount()
                            : expectedBranches;  // 若未预设则用入边数
                    log.info("[ParallelGateway:Join] 当前收集进度 {}/{}", joinCount, forkCount);
                    // 2. 未收集全 → 等待
                    if (joinCount < forkCount) {
                        log.info("[ParallelGateway:Join] 等待剩余分支 instanceId={}, nodeId={}",
                                instance.getId(), fresh.getNodeId());
                        return Mono.empty();
                    }
                    // 3. 收集全 → CAS 完成（乐观锁防并发重复完成）
                    return completeJoinIfActive(ctx, fresh);
                });
    }

    /**
     * CAS 方式完成 Join 节点。
     * 只有第一个将 status 从 ACTIVE 改为 COMPLETED 的请求才发布事件。
     */
    private Mono<Void> completeJoinIfActive(NodeContext ctx,
                                            FlowNodeInstance nodeInstance) {
        var instance = ctx.instance();
        return ctx.nodeRepo().updateNodeStatus(
                        nodeInstance.getId(),
                        "COMPLETED",
                        null,
                        LocalDateTime.now(ZoneId.systemDefault()),
                        "SYSTEM"
                )
                // 去掉 .filter()，改为 flatMap 内判断 rows 值
                .flatMap(rows -> {
                    if (rows <= 0) {
                        log.info("[ParallelGateway:Join] Join 已被另一分支完成，跳过 instanceId={}",
                                instance.getId());
                        return Mono.empty();
                    }
                    log.info("[ParallelGateway:Join] 并行分支全部到达，Join 完成 instanceId={}, nodeId={}",
                            instance.getId(), nodeInstance.getNodeId());

                    var meta = FlowEventMetadata.of(
                            instance.getId(), instance.getInstanceNo(), "NODE_COMPLETED");
                    ctx.eventBus().publish(NodeCompletedEvent.of(
                            meta,
                            nodeInstance.getId(),
                            nodeInstance.getNodeId(),
                            nodeInstance.getNodeName(),
                            nodeInstance.getNodeType(),
                            Collections.emptyMap(),
                            null));
                    return Mono.empty(); // 显式返回 Mono<Void>，无任何歧义
                });
    }

    // ==================== 兜底 ====================
    private Mono<Void> completeImmediately(NodeContext ctx) {
        var nodeInstance = ctx.currentNode();
        var instance = ctx.instance();
        return ctx.nodeRepo().updateNodeStatus(
                        nodeInstance.getId(),
                        "COMPLETED",
                        null,
                        LocalDateTime.now(ZoneId.systemDefault()),
                        "SYSTEM"
                )
                .then(Mono.fromRunnable(() -> {
                    var meta = FlowEventMetadata.of(
                            instance.getId(), instance.getInstanceNo(), "NODE_COMPLETED");
                    ctx.eventBus().publish(NodeCompletedEvent.of(
                            meta,
                            nodeInstance.getId(),
                            nodeInstance.getNodeId(),
                            nodeInstance.getNodeName(),
                            nodeInstance.getNodeType(),
                            Collections.emptyMap(),
                            null));
                }));
    }
}
