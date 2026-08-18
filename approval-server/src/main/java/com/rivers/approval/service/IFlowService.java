package com.rivers.approval.service;

import com.rivers.core.vo.ResultVO;
import com.rivers.proto.StartProcessReq;
import com.rivers.proto.StartProcessRes;
import com.rivers.proto.TerminateInstanceReq;
import reactor.core.publisher.Mono;

/**
 * 流程服务 — 流程发起与终止。
 */
public interface IFlowService {

    /**
     * 发起流程：校验定义 → 创建实例 → 发布 InstanceStartedEvent → 引擎自动推进。
     *
     * @return 创建后的流程实例信息
     */
    Mono<ResultVO<StartProcessRes>> startProcess(StartProcessReq req);

    /**
     * 终止流程实例（管理员操作）。
     */
    Mono<ResultVO<Void>> terminateProcess(TerminateInstanceReq req);
}
