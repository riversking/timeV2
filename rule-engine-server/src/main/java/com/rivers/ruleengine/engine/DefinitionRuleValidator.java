package com.rivers.ruleengine.engine;

import com.rivers.ruleengine.model.GatewayRule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.expression.ParseException;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 流程定义规则校验器（定义创建时校验，错误消息与引擎原校验语义一致）。
 *
 * <p>校验内容：definitionJson 可解析；每个排他网关节点的内嵌规则（config.rules）
 * 逐条校验 condition 非空且 SpEL 语法合法、targetNodeId 属于该网关出边目标集合。
 * 校验通过返回 {@code Optional.empty()}；返回首个错误（列表语义在契约层预留扩展）。
 *
 * <p>以 JSON 树（{@link JsonNode}）解析 definitionJson 而非引擎 DSL 实体类，
 * 使规则服务仅依赖"定义 JSON 契约"（nodes[].id/type/config.rules，edges[].source/target）。
 */
@Component
@Slf4j
public class DefinitionRuleValidator {

    private static final String GATEWAY = "EXCLUSIVE_GATEWAY";

    private final ConditionEvaluator evaluator;
    private final ObjectMapper objectMapper;

    public DefinitionRuleValidator(ConditionEvaluator evaluator, ObjectMapper objectMapper) {
        this.evaluator = evaluator;
        this.objectMapper = objectMapper;
    }

    /**
     * 校验错误：节点 id + 规则下标（非规则级错误为 -1）+ 错误消息。
     */
    public record RuleError(String nodeId, int ruleIndex, String message) {
    }

    /**
     * 校验流程定义 JSON 中的排他网关内嵌规则；返回首个错误，全部通过返回 empty。
     */
    public Optional<RuleError> validate(String definitionJson) {
        JsonNode root;
        try {
            root = objectMapper.readTree(definitionJson);
        } catch (Exception err) {
            log.warn("[DefinitionRuleValidator] 定义 JSON 无法解析", err);
            return Optional.of(new RuleError("", -1, "流程定义 JSON 无法解析: " + err.getMessage()));
        }
        var edges = root.path("edges");
        for (var node : root.path("nodes")) {
            if (!GATEWAY.equals(node.path("type").asString())) {
                continue;
            }
            var nodeId = node.path("id").asString();
            var rawRules = node.path("config").path("rules");
            if (rawRules.isMissingNode() || rawRules.isNull()) {
                continue;
            }
            List<GatewayRule> rules;
            try {
                rules = objectMapper.convertValue(rawRules, new TypeReference<List<GatewayRule>>() {
                });
            } catch (IllegalArgumentException err) {
                return Optional.of(new RuleError(nodeId, -1, "网关内嵌规则格式错误: nodeId=" + nodeId));
            }
            var edgeTargets = edgeTargets(edges, nodeId);
            for (int i = 0; i < rules.size(); i++) {
                var rule = rules.get(i);
                var condition = rule.condition();
                if (condition == null || condition.isBlank()) {
                    return Optional.of(new RuleError(nodeId, i, "condition 不能为空: nodeId=" + nodeId));
                }
                try {
                    evaluator.validate(condition);
                } catch (ParseException err) {
                    return Optional.of(new RuleError(nodeId, i, "condition SpEL 语法错误: " + condition));
                }
                var target = rule.targetNodeId();
                if (target == null || target.isBlank()) {
                    return Optional.of(new RuleError(nodeId, i, "targetNodeId 不能为空: nodeId=" + nodeId));
                }
                if (!edgeTargets.contains(target)) {
                    return Optional.of(new RuleError(nodeId, i,
                            "targetNodeId 不在该网关出边目标集合中: " + target));
                }
            }
        }
        return Optional.empty();
    }

    /** 该节点的出边目标集合（edges[].source == nodeId → edges[].target） */
    private List<String> edgeTargets(JsonNode edges, String sourceNodeId) {
        var targets = new ArrayList<String>();
        for (var edge : edges) {
            if (sourceNodeId.equals(edge.path("source").asString())) {
                targets.add(edge.path("target").asString());
            }
        }
        return targets;
    }
}
