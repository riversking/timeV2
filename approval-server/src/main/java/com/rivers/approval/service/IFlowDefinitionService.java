package com.rivers.approval.service;

import com.rivers.core.vo.ResultVO;
import com.rivers.proto.*;
import reactor.core.publisher.Mono;

/**
 * 流程定义服务 — 定义模板查询与生命周期管理。
 */
public interface IFlowDefinitionService {

    /**
     * 查询最新已发布版本
     */
    Mono<ResultVO<FlowDefinitionRes>> getLatest(GetDefinitionReq req);

    /**
     * 按 key + version 精确查询
     */
    Mono<ResultVO<FlowDefinitionRes>> getByKeyAndVersion(GetDefinitionReq req);

    /**
     * 创建草稿
     */
    Mono<ResultVO<FlowDefinitionRes>> create(CreateDefinitionReq req);

    /**
     * 发布
     */
    Mono<ResultVO<FlowDefinitionRes>> publish(PublishDefinitionReq req);

    /**
     * 停用
     */
    Mono<ResultVO<FlowDefinitionRes>> disable(DisableDefinitionReq req);
}
