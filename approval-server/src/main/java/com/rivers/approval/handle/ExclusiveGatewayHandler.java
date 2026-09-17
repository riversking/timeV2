package com.rivers.approval.handle;

import com.rivers.approval.engine.ConditionEvaluator;
import com.rivers.approval.entity.FlowRule;
import com.rivers.approval.event.FlowEventMetadata;
import com.rivers.approval.event.NodeCompletedEvent;
import com.rivers.approval.model.EdgeDef;
import com.rivers.approval.model.NodeContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 排他网关（XOR Gateway）处理器。
 *
 * <p>路由策略：
 * <ol>
 *   <li>单出边：不查规则、不求值，直接并入该边（"单线选择无规则直接走"）</li>
 *   <li>多出边三级降级：规则链（flow_rule，priority DESC）→ 出边 conditionExpression 按序求值
 *       → 默认边（无条件边）</li>
 *   <li>目标守护：最终 target 必须存在于该网关出边目标集合，否则置实例 FAILED
 *       （消灭"过滤后空边、仅 warn、流程静默卡死"）</li>
 * </ol>
 *
 * <p>发布 NodeCompletedEvent 携带 targetNodeId 与 outputMapping，
 * FlowExecutor 依据 targetNodeId 只推进到指定的那条边。
 */
@Component
@Slf4j
public class ExclusiveGatewayHandler implements NodeHandler {

    private static final String SYSTEM = "SYSTEM";
    private final ConditionEvaluator evaluator;
    private final ObjectMapper objectMapper;

    public ExclusiveGatewayHandler(ConditionEvaluator evaluator, ObjectMapper objectMapper) {
        this.evaluator = evaluator;
        this.objectMapper = objectMapper;
    }

    @Override
    public String supportedType() {
        return "EXCLUSIVE_GATEWAY";
    }

    @Override
    public Mono<Void> handle(NodeContext ctx) {
        var instance = ctx.instance();
        var nodeInstance = ctx.currentNode();
        var definition = ctx.definition();
        var variables = ctx.variables();
        var edges = definition.edgesFrom(nodeInstance.getNodeId());
        // 0. 无出边：直接失败（消灭静默卡死）
        if (edges.isEmpty()) {
            return failInstance(ctx, new IllegalStateException(
                    "排他网关无出边: " + nodeInstance.getNodeId()));
        }
        // 1. 单出边直通：不查规则、不求值
        if (edges.size() == 1) {
            var target = edges.get(0).target();
            log.info("[ExclusiveGateway] 单出边直通 instanceId={}, nodeId={}, target={}",
                    instance.getId(), nodeInstance.getNodeId(), target);
            return completeGateway(ctx, target, Collections.emptyMap())
                    .onErrorResume(err -> failInstance(ctx, err));
        }
        log.info("[ExclusiveGateway] 评估条件 instanceId={}, nodeId={}",
                instance.getId(), nodeInstance.getNodeId());
        // 2. 多出边：规则链 → 边条件 → 默认边（三级降级）
        return ctx.ruleRepo()
                .findByDefAndNode(instance.getDefinitionId(), nodeInstance.getNodeId())
                .collectList()
                .flatMap(rules -> {
                    var route = resolveRoute(rules, edges, nodeInstance.getNodeId(), variables);
                    // 3. 目标守护：路由目标必须在该网关出边目标集合中
                    if (edges.stream().noneMatch(e -> e.target().equals(route.targetNodeId()))) {
                        throw new IllegalStateException(
                                "排他网关路由目标不在出边集合中: nodeId=" + nodeInstance.getNodeId()
                                        + ", target=" + route.targetNodeId());
                    }
                    log.info("[ExclusiveGateway] 路由结果 instanceId={}, targetNodeId={}",
                            instance.getId(), route.targetNodeId());
                    return completeGateway(ctx, route.targetNodeId(), route.outputVariables());
                })
                .onErrorResume(err -> failInstance(ctx, err));
    }

    /**
     * 多出边路由（三级降级）：规则链 → 边条件（按定义顺序，首个 true）→ 默认边。
     */
    private RouteResult resolveRoute(List<FlowRule> rules,
                                     List<EdgeDef> edges,
                                     String nodeId,
                                     Map<String, Object> variables) {
        // 1. 规则链（flow_rule）
        var hit = evaluator.evaluate(rules, variables);
        if (hit.isPresent()) {
            var result = hit.get();
            return new RouteResult(result.targetNodeId(),
                    result.outputVariables() != null ? result.outputVariables() : Map.of());
        }
        // 2. DSL 边条件（按定义顺序，第一条 true 的边）
        for (var edge : edges) {
            var condition = edge.conditionExpression();
            if (condition != null && !condition.isBlank() && evaluator.matches(condition, variables)) {
                log.info("[ExclusiveGateway] 边条件命中 nodeId={}, condition={}, target={}",
                        nodeId, condition, edge.target());
                return new RouteResult(edge.target(), Map.of());
            }
        }
        // 3. 默认边（首条无 conditionExpression 的出边）
        var defaultEdge = edges.stream()
                .filter(e -> e.conditionExpression() == null || e.conditionExpression().isBlank())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "排他网关无规则命中且无默认边: " + nodeId));
        return new RouteResult(defaultEdge.target(), Map.of());
    }

    /**
     * 路由结果：目标节点 + 输出变量（合并进流程变量）
     */
    private record RouteResult(String targetNodeId, Map<String, Object> outputVariables) {
    }

    private Mono<Void> completeGateway(NodeContext ctx,
                                       String targetNodeId,
                                       Map<String, Object> outputVars) {
        var nodeInstance = ctx.currentNode();
        var instance = ctx.instance();
        var toJson = outputVars.isEmpty() ? "" : toJson(outputVars);

        return ctx.nodeRepo().updateNodeStatus(
                        nodeInstance.getId(),
                        "COMPLETED",
                        toJson,
                        LocalDateTime.now(ZoneId.systemDefault()),
                        SYSTEM
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
                            outputVars,
                            targetNodeId));
                }));
    }

    /**
     * 评估失败：实例置 FAILED、网关节点置 SKIPPED，终止推进。
     */
    private Mono<Void> failInstance(NodeContext ctx, Throwable err) {
        var instance = ctx.instance();
        var nodeInstance = ctx.currentNode();
        log.error("[ExclusiveGateway] 条件评估失败，实例置为 FAILED instanceId={}, nodeId={}",
                instance.getId(), nodeInstance.getNodeId(), err);
        return ctx.instanceRepo().updateStatus(
                        instance.getId(), "FAILED",
                        LocalDateTime.now(ZoneId.systemDefault()), SYSTEM)
                .then(ctx.nodeRepo().updateNodeStatus(
                        nodeInstance.getId(), "SKIPPED", "",
                        LocalDateTime.now(ZoneId.systemDefault()), SYSTEM))
                .then();
    }

    private String toJson(Object obj) {
        return objectMapper.writeValueAsString(obj);
    }
}
