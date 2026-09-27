package com.rivers.ruleengine.engine;

import com.rivers.ruleengine.model.GatewayRule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 排他网关路由计算器（多出边三级降级）。
 *
 * <p>三级降级（与引擎原语义一致）：
 * <ol>
 *   <li>内嵌规则（节点 config.rules，priority DESC，首个命中）</li>
 *   <li>DSL 边条件（按定义顺序，第一条 true 的边）</li>
 *   <li>默认边（首条无 conditionExpression 的出边）</li>
 * </ol>
 *
 * <p>单出边直通不经过本计算器（调用方在网关只有 1 条出边时直接并入该边，不求值）。
 * <p>无规则命中且无默认边 → 抛 {@link RuleRouteException}，调用方将流程实例置为 FAILED。
 */
@Component
@Slf4j
public class GatewayRouter {

    /** 命中来源：内嵌规则 */
    public static final String SOURCE_EMBEDDED_RULE = "EMBEDDED_RULE";
    /** 命中来源：DSL 边条件 */
    public static final String SOURCE_EDGE_CONDITION = "EDGE_CONDITION";
    /** 命中来源：默认边 */
    public static final String SOURCE_DEFAULT_EDGE = "DEFAULT_EDGE";

    private final ConditionEvaluator evaluator;

    public GatewayRouter(ConditionEvaluator evaluator) {
        this.evaluator = evaluator;
    }

    /**
     * 出边（target + 条件表达式）；由调用方按定义顺序传入。
     */
    public record Edge(String target, String conditionExpression) {
    }

    /**
     * 路由结果：目标节点 + 输出变量（合并进流程变量）+ 命中来源（便于日志排查）。
     */
    public record RouteResult(String targetNodeId, Map<String, Object> outputVariables, String hitSource) {
    }

    /**
     * 多出边路由（三级降级）：内嵌规则（priority DESC）→ 边条件（按定义顺序，首个 true）→ 默认边。
     *
     * @throws RuleRouteException 无规则命中且无默认边（调用方置实例 FAILED）
     */
    public RouteResult route(String nodeId,
                             List<GatewayRule> rules,
                             List<Edge> edges,
                             Map<String, Object> variables) {
        // 1. 内嵌规则（priority DESC）
        var sorted = rules.stream()
                .sorted(Comparator.comparingInt((GatewayRule r) ->
                        r.priority() == null ? 0 : r.priority()).reversed())
                .toList();
        var hit = evaluator.evaluate(sorted, variables);
        if (hit.isPresent()) {
            var result = hit.get();
            return new RouteResult(result.targetNodeId(),
                    result.outputVariables() != null ? result.outputVariables() : Map.of(),
                    SOURCE_EMBEDDED_RULE);
        }
        // 2. DSL 边条件（按定义顺序，第一条 true 的边）
        for (var edge : edges) {
            var condition = edge.conditionExpression();
            if (condition != null && !condition.isBlank() && evaluator.matches(condition, variables)) {
                log.info("[GatewayRouter] 边条件命中 nodeId={}, condition={}, target={}",
                        nodeId, condition, edge.target());
                return new RouteResult(edge.target(), Map.of(), SOURCE_EDGE_CONDITION);
            }
        }
        // 3. 默认边（首条无 conditionExpression 的出边）
        var defaultEdge = edges.stream()
                .filter(e -> e.conditionExpression() == null || e.conditionExpression().isBlank())
                .findFirst()
                .orElseThrow(() -> new RuleRouteException(
                        "排他网关无规则命中且无默认边: " + nodeId));
        return new RouteResult(defaultEdge.target(), Map.of(), SOURCE_DEFAULT_EDGE);
    }
}
