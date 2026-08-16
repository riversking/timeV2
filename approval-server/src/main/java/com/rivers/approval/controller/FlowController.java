package com.rivers.approval.controller;

import com.rivers.approval.service.IFlowService;
import com.rivers.core.vo.ResultVO;
import com.rivers.proto.StartProcessReq;
import com.rivers.proto.StartProcessRes;
import com.rivers.proto.TerminateInstanceReq;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * 流程控制器 — 流程发起与终止。
 */
@RestController
@RequestMapping("flow")
public class FlowController {

    private final IFlowService flowService;

    public FlowController(IFlowService flowService) {
        this.flowService = flowService;
    }

    @PostMapping("startProcess")
    public Mono<ResultVO<StartProcessRes>> startProcess(@RequestBody StartProcessReq startProcessReq) {
        return flowService.startProcess(startProcessReq);
    }

    @PostMapping("terminateProcess")
    public Mono<ResultVO<Void>> terminateProcess(@RequestBody TerminateInstanceReq terminateInstanceReq) {
        return flowService.terminateProcess(terminateInstanceReq);
    }
}