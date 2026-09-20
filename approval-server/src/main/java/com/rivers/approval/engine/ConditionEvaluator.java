package com.rivers.approval.engine;

import com.rivers.approval.model.GatewayRule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * SpEL 条件评估器：排他网关的内嵌规则与 DSL 边条件共用同一只读求值语义。
 *
 * <p>消费方：
 * <ul>
 *   <li>{@code ExclusiveGatewayHandler} — 内嵌规则（节点 config.rules，priority DESC）取首个命中 / 边条件逐个求值</li>
 *   <li>{@code FlowDefinitionServiceImpl} — 定义创建时内嵌规则的语法校验（parse 不 eval）</li>
 * </ul>
 */
@Component
@Slf4j
public class ConditionEvaluator {

    private final ExpressionParser parser = new SpelExpressionParser();

    public ConditionEvaluator() {
    }

    /**
     * 内嵌规则求值：按 priority DESC（调用方已排序）逐条求值，返回首个命中。
     */
    public Optional<EvalResult> evaluate(List<GatewayRule> rules, Map<String, Object> variables) {
        for (var rule : rules) {
            var condition = rule.condition();
            if (condition == null || condition.isBlank()) {
                continue;
            }
            if (matches(condition, variables)) {
                log.info("[ConditionEvaluator] 内嵌规则命中 condition={}, targetNodeId={}",
                        condition, rule.targetNodeId());
                return Optional.of(new EvalResult(rule.targetNodeId(), rule.outputMapping()));
            }
        }
        log.debug("[ConditionEvaluator] 内嵌规则未命中");
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
     * 仅做 SpEL 语法校验（定义创建校验内嵌规则时使用），不执行求值；
     * 语法非法抛出 org.springframework.expression.ParseException。
     */
    public void validate(String condition) {
        parser.parseExpression(condition);
    }

    public record EvalResult(
            String targetNodeId,
            Map<String, Object> outputVariables) {
    }
}
