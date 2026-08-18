package com.rivers.approval.service;

import com.rivers.core.vo.ResultVO;
import com.rivers.proto.*;
import reactor.core.publisher.Mono;

public interface IFlowInstanceService {

    /**
     * 按实例编号查询
     */
    Mono<ResultVO<FlowInstanceRes>> getByNo(InstanceNoReq req);

    /**
     * 按业务主键查询运行中实例
     */
    Mono<ResultVO<InstanceListRes>> listByBusinessKey(BusinessKeyReq req);

    /**
     * 分页查询某人发起的流程
     */
    Mono<ResultVO<InstanceListRes>> listMyInitiated(ListInstanceReq req);

    /**
     * 按状态分页查询
     */
    Mono<ResultVO<InstanceListRes>> listByStatus(ListInstanceByStatusReq req);
}
