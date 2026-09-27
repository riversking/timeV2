package com.rivers.approval.rule;

import com.rivers.approval.engine.AssigneeResolver;
import com.rivers.approval.engine.ConditionEvaluator;
import com.rivers.approval.model.EdgeDef;
import com.rivers.approval.model.GatewayRule;
import com.rivers.approval.model.ProcessDefinition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.expression.ParseException;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 规则引擎门面·进程内实现（{@code approval.rule-engine.mode=local}，缺省激活）。
 *
 * <p>直调审批服务现有规则类（AssigneeResolver / ConditionEvaluator），
 * 语义与拆分前完全一致；同时作为 remote 模式故障时的应急回滚开关（改配置即切换，零发布）。
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "approval.rule-engine.mode", havingValue = "local", matchIfMissing = true)
public class LocalRuleEngineFacade implements RuleEngineFacade {

    private static final String GATEWAY = "EXCLUSIVE_GATEWAY";

    private final AssigneeResolver assigneeResolver;
    private final ConditionEvaluator evaluator;
    private final ObjectMapper objectMapper;

    public LocalRuleEngineFacade(AssigneeResolver assigneeResolver,
                                 ConditionEvaluator evaluator,
                                 ObjectMapper objectMapper) {
        this.assigneeResolver = assigneeResolver;
        this.evaluator = evaluator;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<List<String>> resolveAssignees(String expr, String initiator, Map<String, Object> variables) {
        return assigneeResolver.resolve(expr, initiator, variables);
    }

    @Override
    public Mono<RouteOutcome> routeGateway(String nodeId, List<GatewayRule> rules,
                                           List<EdgeSpec> edges, Map<String, Object> variables) {
        // 1. 内嵌规则（priority DESC）
        var sorted = rules.stream()
                .sorted(Comparator.comparingInt((GatewayRule r) ->
                        r.priority() == null ? 0 : r.priority()).reversed())
                .toList();
        var hit = evaluator.evaluate(sorted, variables);
        if (hit.isPresent()) {
            var result = hit.get();
            return Mono.just(new RouteOutcome(result.targetNodeId(),
                    result.outputVariables() != null ? result.outputVariables() : Map.of()));
        }
        // 2. DSL 边条件（按定义顺序，第一条 true 的边）
        for (var edge : edges) {
            var condition = edge.conditionExpression();
            if (condition != null && !condition.isBlank() && evaluator.matches(condition, variables)) {
                log.info("[LocalRuleEngine] 边条件命中 nodeId={}, condition={}, target={}",
                        nodeId, condition, edge.target());
                return Mono.just(new RouteOutcome(edge.target(), Map.of()));
            }
        }
        // 3. 默认边（首条无 conditionExpression 的出边）；无默认边 → 错误（调用方置实例 FAILED）
        return edges.stream()
                .filter(e -> e.conditionExpression() == null || e.conditionExpression().isBlank())
                .findFirst()
                .map(e -> Mono.just(new RouteOutcome(e.target(), Map.of())))
                .orElseGet(() -> Mono.error(new IllegalStateException(
                        "排他网关无规则命中且无默认边: " + nodeId)));
    }

    @Override
    public Mono<Optional<String>> validateDefinition(String definitionJson) {
        return Mono.just(doValidate(definitionJson));
    }

    /**
     * 定义级校验：definitionJson 可解析为 DSL；每个排他网关节点的内嵌规则（config.rules）
     * 逐条校验 condition 非空且 SpEL 语法合法、targetNodeId 属于该网关出边目标集合。
     */
    private Optional<String> doValidate(String definitionJson) {
        ProcessDefinition definition;
        try {
            definition = objectMapper.readValue(definitionJson, ProcessDefinition.class);
        } catch (Exception err) {
            log.warn("[LocalRuleEngine] 定义 JSON 无法解析", err);
            return Optional.of("流程定义 JSON 无法解析: " + err.getMessage());
        }
        for (var node : definition.nodes()) {
            if (!GATEWAY.equals(node.type())) {
                continue;
            }
            var rawRules = node.config("rules");
            if (rawRules == null) {
                continue;
            }
            List<GatewayRule> rules;
            try {
                rules = objectMapper.convertValue(rawRules, new TypeReference<List<GatewayRule>>() {
                });
            } catch (IllegalArgumentException err) {
                return Optional.of("网关内嵌规则格式错误: nodeId=" + node.id());
            }
            var edgeTargets = definition.edgesFrom(node.id()).stream()
                    .map(EdgeDef::target)
                    .toList();
            for (var rule : rules) {
                var condition = rule.condition();
                if (condition == null || condition.isBlank()) {
                    return Optional.of("condition 不能为空: nodeId=" + node.id());
                }
                try {
                    evaluator.validate(condition);
                } catch (ParseException err) {
                    return Optional.of("condition SpEL 语法错误: " + condition);
                }
                var target = rule.targetNodeId();
                if (target == null || target.isBlank()) {
                    return Optional.of("targetNodeId 不能为空: nodeId=" + node.id());
                }
                if (!edgeTargets.contains(target)) {
                    return Optional.of("targetNodeId 不在该网关出边目标集合中: " + target);
                }
            }
        }
        return Optional.empty();
    }
}
