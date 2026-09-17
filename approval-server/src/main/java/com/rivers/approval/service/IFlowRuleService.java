package com.rivers.approval.service;

import com.rivers.core.vo.ResultVO;
import com.rivers.proto.*;
import reactor.core.publisher.Mono;

/**
 * 规则服务 — flow_rule 流转规则的写入校验与管理查询。
 * <p>
 * 规则用途：排他网关分支路由（condition → targetNodeId），
 * 绑定 definitionId + nodeId，按 priority DESC 求值，仅决定"下一步走哪个节点"。
 */
public interface IFlowRuleService {

    /**
     * 创建规则（校验：定义存在 / 节点为排他网关 / SpEL 语法 / 目标属于出边集合）
     */
    Mono<ResultVO<Void>> create(CreateFlowRuleReq req);

    /**
     * 更新规则（ruleCode / definitionId 不可变）
     */
    Mono<ResultVO<Void>> update(UpdateFlowRuleReq req);

    /**
     * 软删除
     */
    Mono<ResultVO<Void>> delete(DeleteFlowRuleReq req);

    /**
     * 启用/禁用（0-禁用, 1-启用）
     */
    Mono<ResultVO<Void>> toggleEnabled(ToggleFlowRuleReq req);

    /**
     * 查询单条
     */
    Mono<ResultVO<FlowRuleRes>> get(GetFlowRuleReq req);

    /**
     * 按定义分页查询（含禁用规则；nodeId 可选过滤）
     */
    Mono<ResultVO<FlowRuleListRes>> listByDefinition(ListFlowRuleReq req);
}
