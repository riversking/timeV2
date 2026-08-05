package com.rivers.approval.engine;

import com.rivers.approval.entity.FlowRule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * SpEL 条件评估器，用于排他网关的分支路由。
 *
 * <p>输入：规则链（按优先级排序）+ 流程变量
 * <br>输出：第一个匹配的规则（含 targetNodeId / outputMapping）
 *
 * <p>规则链来源：flow_rule 表中 definition_id + node_id 对应的规则，
 * 按 priority DESC 排序后逐一评估。
 */
@Component
@Slf4j
public class ConditionEvaluator {


    private final ExpressionParser parser = new SpelExpressionParser();
    private final ObjectMapper objectMapper;

    public ConditionEvaluator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Optional<EvalResult> evaluate(List<FlowRule> rules, Map<String, Object> variables) {
        var ctx = new StandardEvaluationContext();
        variables.forEach(ctx::setVariable);
        for (var rule : rules) {
            var ruleConfig = parseRuleConfig(rule.getRuleConfig());
            for (var config : ruleConfig) {
                var condition = config.condition();
                if (condition == null || condition.isBlank()) {
                    continue;
                }
                var expr = parser.parseExpression(condition);
                var result = expr.getValue(ctx, Boolean.class);
                if (Boolean.TRUE.equals(result)) {
                    log.info("[ConditionEvaluator] 规则命中 ruleCode={}, condition={}, targetNodeId={}",
                            rule.getRuleCode(), condition, config.targetNodeId());
                    return Optional.of(new EvalResult(config.targetNodeId(), config.outputMapping()));
                }
            }
        }
        log.warn("[ConditionEvaluator] 无规则命中，将走默认路径");
        return Optional.empty();
    }

    private List<RuleConfigItem> parseRuleConfig(String json) {
        var list = objectMapper.readValue(json,
                new TypeReference<List<Map<String, Object>>>() {
                });
        return list.stream()
                .map(map -> new RuleConfigItem(
                        (String) map.get("condition"),
                        (String) map.get("targetNodeId"),
                        (Map<String, Object>) map.get("outputMapping")))
                .toList();
    }

    public record RuleConfigItem(
            String condition,
            String targetNodeId,
            Map<String, Object> outputMapping) {
    }

    public record EvalResult(
            String targetNodeId,
            Map<String, Object> outputVariables) {
    }
}
