package com.rivers.approval.engine;

import com.rivers.approval.entity.FlowRule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * SpEL 条件评估器：排他网关的规则链与 DSL 边条件共用同一只读求值语义。
 *
 * <p>消费方：
 * <ul>
 *   <li>{@code ExclusiveGatewayHandler} — 规则链（flow_rule，priority DESC）取首个命中 / 边条件逐个求值</li>
 *   <li>{@code FlowRuleServiceImpl} — 规则写入时的语法校验（parse 不 eval）</li>
 * </ul>
 */
@Component
@Slf4j
public class ConditionEvaluator {

    private final ExpressionParser parser = new SpelExpressionParser();
    private final ObjectMapper objectMapper;

    public ConditionEvaluator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 规则链求值：按 priority DESC 逐条、逐 config 求值，返回首个命中。
     */
    public Optional<EvalResult> evaluate(List<FlowRule> rules, Map<String, Object> variables) {
        for (var rule : rules) {
            var ruleConfig = parseRuleConfig(rule.getRuleConfig());
            for (var config : ruleConfig) {
                var condition = config.condition();
                if (condition == null || condition.isBlank()) {
                    continue;
                }
                if (matches(condition, variables)) {
                    log.info("[ConditionEvaluator] 规则命中 ruleCode={}, condition={}, targetNodeId={}",
                            rule.getRuleCode(), condition, config.targetNodeId());
                    return Optional.of(new EvalResult(config.targetNodeId(), config.outputMapping()));
                }
            }
        }
        log.debug("[ConditionEvaluator] 规则链未命中");
        return Optional.empty();
    }

    /**
     * 单表达式求值（规则链与 DSL 边条件共用）。
     * SimpleEvaluationContext.forReadOnlyDataBinding：
     * 禁用 T(...) 类型引用、构造器与任意方法调用，仅允许属性访问，
     * 防止恶意规则注入执行任意代码（如 T(java.lang.Runtime).getRuntime().exec(...)）
     */
    public boolean matches(String condition, Map<String, Object> variables) {
        var ctx = SimpleEvaluationContext.forReadOnlyDataBinding().build();
        if (variables != null) {
            variables.forEach(ctx::setVariable);
        }
        var result = parser.parseExpression(condition).getValue(ctx, Boolean.class);
        return Boolean.TRUE.equals(result);
    }

    /**
     * 仅做 SpEL 语法校验（规则写入时使用），不执行求值；
     * 语法非法抛出 org.springframework.expression.ParseException。
     */
    public void validate(String condition) {
        parser.parseExpression(condition);
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
