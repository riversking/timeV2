package com.rivers.approval.service.impl;

import com.rivers.approval.entity.FlowInstance;
import com.rivers.approval.repository.FlowInstanceRepository;
import com.rivers.approval.service.IFlowInstanceService;
import com.rivers.core.vo.ResultVO;
import com.rivers.proto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * 流程实例查询服务实现。
 */
@Service
@Slf4j
public class FlowInstanceServiceImpl implements IFlowInstanceService {

    private static final String YYYY_MM_DD_HH_MM_SS = "yyyy-MM-dd HH:mm:ss";

    private final FlowInstanceRepository instanceRepo;

    public FlowInstanceServiceImpl(FlowInstanceRepository instanceRepo) {
        this.instanceRepo = instanceRepo;
    }

    @Override
    public Mono<ResultVO<FlowInstanceRes>> getByNo(InstanceNoReq req) {
        return instanceRepo.findByInstanceNo(req.getInstanceNo())
                .switchIfEmpty(Mono.<FlowInstance>error(
                        new IllegalArgumentException("流程实例不存在: " + req.getInstanceNo())))
                .map(this::toInstanceRes)
                .map(ResultVO::ok);
    }

    @Override
    public Mono<ResultVO<InstanceListRes>> listByBusinessKey(BusinessKeyReq req) {
        return instanceRepo.findRunningByBusinessKey(req.getBusinessKey())
                .map(this::toInstanceRes)
                .collectList()
                .map(list -> ResultVO.ok(
                        InstanceListRes.newBuilder().addAllInstances(list).build()));
    }

    @Override
    public Mono<ResultVO<InstanceListRes>> listMyInitiated(ListInstanceReq req) {
        var offset = (req.getCurrentPage() - 1) * req.getPageSize();
        var loginUser = req.getLoginUser();
        var userId = loginUser.getUserId();
        return instanceRepo.findByInitiatorWithPage(
                        userId, offset, req.getPageSize())
                .map(this::toInstanceRes)
                .collectList()
                .map(list -> ResultVO.ok(
                        InstanceListRes.newBuilder()
                                .addAllInstances(list)
                                .build()));
    }

    @Override
    public Mono<ResultVO<InstanceListRes>> listByStatus(ListInstanceByStatusReq req) {
        var offset = (req.getCurrentPage() - 1) * req.getPageSize();
        return instanceRepo.findByStatusWithPage(
                        req.getStatus(), offset, req.getPageSize())
                .map(this::toInstanceRes)
                .collectList()
                .map(list -> ResultVO.ok(
                        InstanceListRes.newBuilder().addAllInstances(list).build()));
    }

    // ==================== 辅助 ====================

    /**
     * Entity → FlowInstanceRes 转换
     */
    private FlowInstanceRes toInstanceRes(FlowInstance i) {
        return FlowInstanceRes.newBuilder()
                .setId(i.getId())
                .setInstanceNo(i.getInstanceNo())
                .setDefinitionId(i.getDefinitionId())
                .setDefinitionKey(i.getDefinitionKey())
                .setDefinitionVersion(i.getDefinitionVersion())
                .setTitle(i.getTitle())
                .setInitiator(i.getInitiator())
                .setInitiatorName(i.getInitiatorName())
                .setBusinessKey(i.getBusinessKey())
                .setStatus(i.getStatus())
                .setVariables(i.getVariables())
                .setCurrentNodeIds(i.getCurrentNodeIds())
                .setStartTime(formatTime(i.getStartTime()))
                .setEndTime(formatTime(i.getEndTime()))
                .build();
    }

    private String formatTime(java.time.LocalDateTime time) {
        return Optional.ofNullable(time)
                .map(t -> DateTimeFormatter.ofPattern(YYYY_MM_DD_HH_MM_SS).format(t))
                .orElse("");
    }
}