package com.rivers.approval.handle;

import com.rivers.approval.entity.FlowNodeInstance;
import com.rivers.approval.event.FlowEventMetadata;
import com.rivers.approval.event.NodeCompletedEvent;
import com.rivers.approval.model.EdgeDef;
import com.rivers.approval.model.NodeContext;
import com.rivers.approval.model.NodeDef;
import com.rivers.approval.model.ProcessDefinition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * 并行网关（Parallel Gateway）处理器。
 *
 * <p>根据拓扑自动检测 Fork / Join 模式：
 *
 * <h3>Fork（分叉，出边 ≥ 2）</h3>
 * <ol>
 *   <li>为下游 Join 网关预创建节点实例并写入 fork_count（入边数），
 *       保证所有分支汇聚到同一个实例</li>
 *   <li>标记网关节点 COMPLETED</li>
 *   <li>发布 NodeCompletedEvent（不带 targetNodeId），
 *       由 FlowExecutor 并行走全部出边创建子节点实例</li>
 * </ol>
 *
 * <h3>Join（汇聚，入边 ≥ 2）</h3>
 * <ol>
 *   <li>原子递增 join_count（所有分支复用 Fork 预创建的同一实例）</li>
 *   <li>join_count &lt; fork_count → 等待剩余分支，不推进</li>
 *   <li>join_count == fork_count → CAS 置为 COMPLETED，发布 NodeCompletedEvent</li>
 * </ol>
 *
 * <p>并发安全：多条分支可能同时到达 Join 节点：
 * <ul>
 *   <li>join_count 由数据库原子递增（SET join_count = join_count + 1）</li>
 *   <li>完成时用 WHERE status = 'ACTIVE' 做乐观锁，只有第一个成功 CAS 才发布事件</li>
 * </ul>
 */
@Component
@Slf4j
public class ParallelGatewayHandler implements NodeHandler {

    private static final String NODE_COMPLETED = "NODE_COMPLETED";
    private static final String COMPLETED = "COMPLETED";
    private static final String SYSTEM = "SYSTEM";

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
            return handleFork(ctx, outgoingEdges.size());
        }
        // 汇聚网关：入边 ≥ 2 → Join
        if (incomingCount >= 2) {
            return handleJoin(ctx, (int) incomingCount);
        }
        // 单进单出 = 普通节点，直接完成
        log.warn("[ParallelGateway] 非典型并行网关 nodeId={}, 入边={}, 出边={}",
                nodeId, incomingCount, outgoingEdges.size());
        return completeImmediately(ctx);
    }

    // ==================== Fork ====================

    /**
     * Fork 模式。
     * 与类注释一致：先为下游 Join 网关预创建节点实例并写入 fork_count，
     * 再标记自身完成，最后发布 NodeCompletedEvent（不带 targetNodeId，
     * 由 FlowExecutor 并行走全部出边创建子节点实例）。
     */
    private Mono<Void> handleFork(NodeContext ctx, int forkCount) {
        var nodeInstance = ctx.currentNode();
        var instance = ctx.instance();
        log.info("[ParallelGateway:Fork] instanceId={}, nodeId={}, 分叉数={}",
                instance.getId(), nodeInstance.getNodeId(), forkCount);
        // 1. 预创建下游 Join 节点实例（写入 fork_count），保证所有分支汇聚到同一实例
        return preCreateJoinNodes(ctx, nodeInstance.getNodeId())
                // 2. 标记网关节点 COMPLETED
                .then(ctx.nodeRepo().updateNodeStatus(
                        nodeInstance.getId(),
                        COMPLETED,
                        "",
                        LocalDateTime.now(ZoneId.systemDefault()),
                        SYSTEM
                ))
                // 3. 发布 NodeCompletedEvent → FlowExecutor 并行走所有出边
                .then(Mono.fromRunnable(() -> {
                    var meta = FlowEventMetadata.of(
                            instance.getId(), instance.getInstanceNo(), NODE_COMPLETED);
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

    // ==================== Fork：预创建下游 Join ====================

    /**
     * 预创建 Fork 下游所有 Join 网关的节点实例并写入 fork_count（入边数）。
     * 已存在 ACTIVE 实例（如上游 Fork 已创建）则复用，保证汇聚到唯一实例。
     */
    private Mono<Void> preCreateJoinNodes(NodeContext ctx, String forkNodeId) {
        var joinNodeIds = findDownstreamJoinNodeIds(ctx.definition(), forkNodeId);
        if (joinNodeIds.isEmpty()) {
            return Mono.empty();
        }
        return Flux.fromIterable(joinNodeIds)
                .flatMap(joinId -> ctx.definition().nodeById(joinId)
                        .map(joinNode -> ctx.nodeRepo()
                                .findActiveByInstanceIdAndNodeId(ctx.instance().getId(), joinId)
                                .switchIfEmpty(createJoinInstance(ctx, joinNode,
                                        incomingCount(ctx.definition(), joinId)))
                                .then())
                        .orElse(Mono.empty()))
                .then();
    }

    /**
     * BFS 查找 Fork 下游的 Join 网关（入边≥2 的 PARALLEL_GATEWAY），
     * 遇到 Join 即收录并停止向下，visited 防环。
     */
    private List<String> findDownstreamJoinNodeIds(ProcessDefinition definition,
                                                   String forkNodeId) {
        var result = new ArrayList<String>();
        var visited = new HashSet<String>();
        var queue = new ArrayDeque<>(definition.edgesFrom(forkNodeId).stream()
                .map(EdgeDef::target).toList());
        while (!queue.isEmpty()) {
            var nodeId = queue.poll();
            if (visited.add(nodeId)) {
                definition.nodeById(nodeId).ifPresent(node -> {
                    if (isJoinGateway(definition, node)) {
                        result.add(nodeId);
                    } else {
                        definition.edgesFrom(nodeId).forEach(e -> queue.add(e.target()));
                    }
                });
            }
        }
        return result;
    }

    private Mono<FlowNodeInstance> createJoinInstance(NodeContext ctx,
                                                      NodeDef joinNode,
                                                      int forkCount) {
        var ni = FlowNodeInstance.builder()
                .instanceId(ctx.instance().getId())
                .nodeId(joinNode.id())
                .nodeName(joinNode.name())
                .nodeType(joinNode.type())
                .status("ACTIVE")
                .forkCount(forkCount)
                .joinCount(0)
                .startTime(LocalDateTime.now(ZoneId.systemDefault()))
                .createUser(SYSTEM)
                .updateUser(SYSTEM)
                .build();
        return ctx.nodeRepo().save(ni)
                .doOnNext(saved -> log.info(
                        "[ParallelGateway:Fork] 预创建 Join 节点实例 nodeInstanceId={}, nodeId={}, forkCount={}",
                        saved.getId(), joinNode.id(), forkCount));
    }

    private boolean isJoinGateway(ProcessDefinition definition, NodeDef node) {
        return "PARALLEL_GATEWAY".equals(node.type())
                && incomingCount(definition, node.id()) >= 2;
    }

    private int incomingCount(ProcessDefinition definition, String nodeId) {
        return (int) definition.edges().stream()
                .filter(e -> e.target().equals(nodeId)).count();
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
                        COMPLETED,
                        "",
                        LocalDateTime.now(ZoneId.systemDefault()),
                        SYSTEM
                )
                .flatMap(rows -> {
                    if (rows <= 0) {
                        log.info("[ParallelGateway:Join] Join 已被另一分支完成，跳过 instanceId={}",
                                instance.getId());
                        return Mono.empty();
                    }
                    log.info("[ParallelGateway:Join] 并行分支全部到达，Join 完成 instanceId={}, nodeId={}",
                            instance.getId(), nodeInstance.getNodeId());

                    var meta = FlowEventMetadata.of(
                            instance.getId(), instance.getInstanceNo(), NODE_COMPLETED);
                    ctx.eventBus().publish(NodeCompletedEvent.of(
                            meta,
                            nodeInstance.getId(),
                            nodeInstance.getNodeId(),
                            nodeInstance.getNodeName(),
                            nodeInstance.getNodeType(),
                            Collections.emptyMap(),
                            null));
                    return Mono.empty();
                });
    }

    // ==================== 兜底 ====================
    private Mono<Void> completeImmediately(NodeContext ctx) {
        var nodeInstance = ctx.currentNode();
        var instance = ctx.instance();
        return ctx.nodeRepo().updateNodeStatus(
                        nodeInstance.getId(),
                        COMPLETED,
                        "",
                        LocalDateTime.now(ZoneId.systemDefault()),
                        SYSTEM
                )
                .then(Mono.fromRunnable(() -> {
                    var meta = FlowEventMetadata.of(
                            instance.getId(), instance.getInstanceNo(), NODE_COMPLETED);
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