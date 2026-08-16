package com.rivers.approval.controller;

import com.rivers.approval.service.IFlowDefinitionService;
import com.rivers.core.vo.ResultVO;
import com.rivers.proto.*;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * 流程定义控制器 — 定义模板查询与生命周期管理。
 */
@RestController
@RequestMapping("flowDefinition")
public class FlowDefinitionController {

    private final IFlowDefinitionService definitionService;

    public FlowDefinitionController(IFlowDefinitionService definitionService) {
        this.definitionService = definitionService;
    }

    @PostMapping("getLatest")
    public Mono<ResultVO<FlowDefinitionRes>> getLatest(@RequestBody GetDefinitionReq getDefinitionReq) {
        return definitionService.getLatest(getDefinitionReq);
    }

    @PostMapping("getByKeyAndVersion")
    public Mono<ResultVO<FlowDefinitionRes>> getByKeyAndVersion(@RequestBody GetDefinitionReq getDefinitionReq) {
        return definitionService.getByKeyAndVersion(getDefinitionReq);
    }

    @PostMapping("create")
    public Mono<ResultVO<FlowDefinitionRes>> create(@RequestBody CreateDefinitionReq createDefinitionReq) {
        return definitionService.create(createDefinitionReq);
    }

    @PostMapping("publish")
    public Mono<ResultVO<FlowDefinitionRes>> publish(@RequestBody PublishDefinitionReq publishDefinitionReq) {
        return definitionService.publish(publishDefinitionReq);
    }

    @PostMapping("disable")
    public Mono<ResultVO<FlowDefinitionRes>> disable(@RequestBody DisableDefinitionReq disableDefinitionReq) {
        return definitionService.disable(disableDefinitionReq);
    }
}