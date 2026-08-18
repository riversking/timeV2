package com.rivers.approval.service;

import com.rivers.core.vo.ResultVO;
import com.rivers.proto.*;
import reactor.core.publisher.Mono;

/**
 * 任务服务 — 任务池全生命周期操作。
 */
public interface ITaskService {

    /**
     * 我的待办
     */
    Mono<ResultVO<TaskListRes>> listTodo(ListTaskReq req);

    /**
     * 待认领池
     */
    Mono<ResultVO<TaskListRes>> listClaimable(ListTaskReq req);

    /**
     * 任务详情
     */
    Mono<ResultVO<FlowTaskRes>> getByTaskNo(TaskNoReq req);

    /**
     * 认领
     */
    Mono<ResultVO<Void>> claim(ClaimTaskReq req);

    /**
     * 完成（审批）
     */
    Mono<ResultVO<FlowTaskRes>> complete(CompleteTaskReq req);

    /**
     * 取消
     */
    Mono<ResultVO<Void>> cancel(CancelTaskReq req);

    /**
     * 转交
     */
    Mono<ResultVO<Void>> transfer(TransferTaskReq req);
}