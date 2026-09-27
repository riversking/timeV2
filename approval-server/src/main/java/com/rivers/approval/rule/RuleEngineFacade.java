package com.rivers.approval.rule;

import com.rivers.approval.model.GatewayRule;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 规则引擎接入门面（审批服务唯一规则调用入口）。
 *
 * <p>双实现按 {@code approval.rule-engine.mode} 切换：
 * <ul>
 *   <li>{@code local}（默认）— {@link LocalRuleEngineFacade}：进程内直调现有规则类，应急回滚开关</li>
 *   <li>{@code remote} — {@link RemoteRuleEngineFacade}：调用独立 rule-engine-server（nacos 发现）</li>
 * </ul>
 *
 * <p>三个能力与规则服务契约一一对应：
 * 审批人表达式解析 / 排他网关路由（多出边三级降级）/ 定义内嵌规则校验。
 */
public interface RuleEngineFacade {

    /**
     * 审批人表达式解析：返回保序去重处理人列表（未解析 token 保留字面原文）。
     *
     * @param expr      节点 config.candidateExpr 原文
     * @param initiator 发起人（$startUser 数据源）
     * @param variables 流程变量（leaderChain / 变量通道 / 兜底查询上下文）
     */
    Mono<List<String>> resolveAssignees(String expr, String initiator, Map<String, Object> variables);

    /**
     * 排他网关路由（多出边三级降级）：内嵌规则 → 边条件 → 默认边。
     * 无规则命中且无默认边 → {@code Mono.error}（调用方置实例 FAILED）。
     *
     * @param nodeId    网关节点 id（日志与错误消息）
     * @param rules     网关内嵌规则（config.rules，可为空 → 直接走边条件/默认边）
     * @param edges     网关出边（按定义顺序）
     * @param variables 流程变量
     */
    Mono<RouteOutcome> routeGateway(String nodeId, List<GatewayRule> rules,
                                    List<EdgeSpec> edges, Map<String, Object> variables);

    /**
     * 定义内嵌规则校验（定义创建时调用）。
     * 返回 empty 表示校验通过；有值表示首个错误消息。
     */
    Mono<Optional<String>> validateDefinition(String definitionJson);

    /**
     * 网关路由结果：目标节点 + 输出变量（合并进流程变量）。
     */
    record RouteOutcome(String targetNodeId, Map<String, Object> outputVariables) {
    }

    /**
     * 网关出边（target + 条件表达式，按定义顺序）。
     */
    record EdgeSpec(String target, String conditionExpression) {
    }
}
