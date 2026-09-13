package com.rivers.approval.service;

import com.rivers.core.vo.ResultVO;
import com.rivers.proto.*;
import reactor.core.publisher.Mono;

/**
 * 流程历史服务 — 审计追踪查询。
 */
public interface IFlowHistoryService {

    /**
     * 某实例的完整历史轨迹
     */
    Mono<ResultVO<HistoryListRes>> listByInstance(InstanceIdReq req);

    /**
     * 某任务的审批流转记录
     */
    Mono<ResultVO<HistoryListRes>> listByTask(TaskIdReq req);

    /**
     * 某人的操作记录（分页）
     */
    Mono<ResultVO<HistoryListRes>> listByOperator(OperatorHistoryReq req);

    Mono<ResultVO<FlowTrackRes>> trackByInstance(InstanceIdReq req);
}
