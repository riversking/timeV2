package com.rivers.approval.controller;

import com.rivers.approval.service.IFlowHistoryService;
import com.rivers.core.vo.ResultVO;
import com.rivers.proto.HistoryListRes;
import com.rivers.proto.InstanceIdReq;
import com.rivers.proto.OperatorHistoryReq;
import com.rivers.proto.TaskIdReq;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * 流程历史控制器 — 审计追踪查询。
 */
@RestController
@RequestMapping("flowHistory")
public class FlowHistoryController {

    private final IFlowHistoryService historyService;

    public FlowHistoryController(IFlowHistoryService historyService) {
        this.historyService = historyService;
    }

    @PostMapping("listByInstance")
    public Mono<ResultVO<HistoryListRes>> listByInstance(@RequestBody InstanceIdReq instanceIdReq) {
        return historyService.listByInstance(instanceIdReq);
    }

    @PostMapping("listByTask")
    public Mono<ResultVO<HistoryListRes>> listByTask(@RequestBody TaskIdReq taskIdReq) {
        return historyService.listByTask(taskIdReq);
    }

    @PostMapping("listByOperator")
    public Mono<ResultVO<HistoryListRes>> listByOperator(@RequestBody OperatorHistoryReq operatorHistoryReq) {
        return historyService.listByOperator(operatorHistoryReq);
    }
}