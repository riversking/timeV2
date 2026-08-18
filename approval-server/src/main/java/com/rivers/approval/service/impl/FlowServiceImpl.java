package com.rivers.approval.service.impl;

import com.rivers.approval.entity.FlowInstance;
import com.rivers.approval.event.FlowEventBus;
import com.rivers.approval.event.FlowEventMetadata;
import com.rivers.approval.event.InstanceStartedEvent;
import com.rivers.approval.repository.FlowDefinitionRepository;
import com.rivers.approval.repository.FlowInstanceRepository;
import com.rivers.approval.service.IFlowService;
import com.rivers.core.vo.ResultVO;
import com.rivers.proto.StartProcessReq;
import com.rivers.proto.StartProcessRes;
import com.rivers.proto.TerminateInstanceReq;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

/**
 * 流程服务实现 — 流程发起与终止。
 */
@Service
@Slf4j
public class FlowServiceImpl implements IFlowService {

    private final FlowDefinitionRepository defRepo;
    private final FlowInstanceRepository instanceRepo;
    private final FlowEventBus eventBus;
    private final ObjectMapper objectMapper;

    public FlowServiceImpl(FlowDefinitionRepository defRepo,
                           FlowInstanceRepository instanceRepo,
                           FlowEventBus eventBus,
                           ObjectMapper objectMapper) {
        this.defRepo = defRepo;
        this.instanceRepo = instanceRepo;
        this.eventBus = eventBus;
        this.objectMapper = objectMapper;
    }

    /**
     * 发起流程。
     *
     * @return 创建后的流程实例
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Mono<ResultVO<StartProcessRes>> startProcess(StartProcessReq req) {
        var initiator = req.getInitiator();
        var initiatorName = req.getInitiatorName();
        var variables = req.getVariables();
        return defRepo.findLatestPublishedByKey(req.getDefinitionKey())
                .switchIfEmpty(Mono.error(
                        new IllegalArgumentException("流程定义不存在或未发布: " + req.getDefinitionKey())))
                .flatMap(def -> {
                    var instanceNo = "PI-" + UUID.randomUUID().toString()
                            .replace("-", "").substring(0, 16);
                    var instance = FlowInstance.builder()
                            .instanceNo(instanceNo)
                            .definitionId(def.getId())
                            .definitionKey(def.getDefinitionKey())
                            .definitionVersion(def.getVersion())
                            .title(req.getTitle())
                            .initiator(initiator)
                            .initiatorName(initiatorName)
                            .businessKey(req.getBusinessKey())
                            .status("RUNNING")
                            .variables(toJson(variables))
                            .startTime(LocalDateTime.now(ZoneId.systemDefault()))
                            .createUser(initiator)
                            .updateUser(initiator)
                            .build();
                    return instanceRepo.save(instance);
                })
                .map(instance -> {
                    log.info("[FlowServiceImpl] 流程发起成功 instanceId={}, instanceNo={}",
                            instance.getId(), instance.getInstanceNo());
                    var meta = FlowEventMetadata.of(
                            instance.getId(), instance.getInstanceNo(), "INSTANCE_STARTED");
                    eventBus.publish(InstanceStartedEvent.of(
                            meta, instance.getDefinitionId(),
                            instance.getDefinitionKey(), initiator));
                    var res = StartProcessRes.newBuilder()
                            .setInstanceId(instance.getId())
                            .setInstanceNo(instance.getInstanceNo())
                            .setStatus(instance.getStatus())
                            .build();
                    return ResultVO.ok(res);
                });
    }

    /**
     * 终止流程实例（管理员操作）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Mono<ResultVO<Void>> terminateProcess(TerminateInstanceReq req) {
        return instanceRepo.findById(req.getInstanceId())
                .flatMap(instance -> {
                    if (!"RUNNING".equals(instance.getStatus())) {
                        return Mono.<FlowInstance>error(
                                new IllegalStateException("只能终止运行中的流程"));
                    }
                    return instanceRepo.updateStatus(
                            req.getInstanceId(), "TERMINATED",
                            LocalDateTime.now(ZoneId.systemDefault()), req.getOperator());
                })
                .thenReturn(ResultVO.ok());
    }

    private String toJson(Object obj) {
        return objectMapper.writeValueAsString(obj);
    }
}