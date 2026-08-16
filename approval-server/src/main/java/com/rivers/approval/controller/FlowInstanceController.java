package com.rivers.approval.controller;

import com.rivers.approval.service.IFlowInstanceService;
import com.rivers.core.vo.ResultVO;
import com.rivers.proto.*;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * 流程实例控制器 — 实例查询。
 */
@RestController
@RequestMapping("flowInstance")
public class FlowInstanceController {

    private final IFlowInstanceService instanceService;

    public FlowInstanceController(IFlowInstanceService instanceService) {
        this.instanceService = instanceService;
    }

    @PostMapping("getByNo")
    public Mono<ResultVO<FlowInstanceRes>> getByNo(@RequestBody InstanceNoReq instanceNoReq) {
        return instanceService.getByNo(instanceNoReq);
    }

    @PostMapping("listByBusinessKey")
    public Mono<ResultVO<InstanceListRes>> listByBusinessKey(@RequestBody BusinessKeyReq businessKeyReq) {
        return instanceService.listByBusinessKey(businessKeyReq);
    }

    @PostMapping("listMyInitiated")
    public Mono<ResultVO<InstanceListRes>> listMyInitiated(@RequestBody ListInstanceReq listInstanceReq) {
        return instanceService.listMyInitiated(listInstanceReq);
    }

    @PostMapping("listByStatus")
    public Mono<ResultVO<InstanceListRes>> listByStatus(@RequestBody ListInstanceByStatusReq listInstanceByStatusReq) {
        return instanceService.listByStatus(listInstanceByStatusReq);
    }
}