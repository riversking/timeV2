package com.rivers.approval.controller;

import com.rivers.approval.service.IFlowRuleService;
import com.rivers.core.vo.ResultVO;
import com.rivers.proto.*;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * 规则控制器 — flow_rule 流转规则管理。
 */
@RestController
@RequestMapping("flowRule")
public class FlowRuleController {

    private final IFlowRuleService ruleService;

    public FlowRuleController(IFlowRuleService ruleService) {
        this.ruleService = ruleService;
    }

    @PostMapping("create")
    public Mono<ResultVO<Void>> create(@RequestBody CreateFlowRuleReq createFlowRuleReq) {
        return ruleService.create(createFlowRuleReq);
    }

    @PostMapping("update")
    public Mono<ResultVO<Void>> update(@RequestBody UpdateFlowRuleReq updateFlowRuleReq) {
        return ruleService.update(updateFlowRuleReq);
    }

    @PostMapping("delete")
    public Mono<ResultVO<Void>> delete(@RequestBody DeleteFlowRuleReq deleteFlowRuleReq) {
        return ruleService.delete(deleteFlowRuleReq);
    }

    @PostMapping("toggleEnabled")
    public Mono<ResultVO<Void>> toggleEnabled(@RequestBody ToggleFlowRuleReq toggleFlowRuleReq) {
        return ruleService.toggleEnabled(toggleFlowRuleReq);
    }

    @PostMapping("get")
    public Mono<ResultVO<FlowRuleRes>> get(@RequestBody GetFlowRuleReq getFlowRuleReq) {
        return ruleService.get(getFlowRuleReq);
    }

    @PostMapping("listByDefinition")
    public Mono<ResultVO<FlowRuleListRes>> listByDefinition(@RequestBody ListFlowRuleReq listFlowRuleReq) {
        return ruleService.listByDefinition(listFlowRuleReq);
    }
}
