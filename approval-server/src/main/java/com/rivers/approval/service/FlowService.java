package com.rivers.approval.service;

import com.rivers.approval.entity.FlowInstance;
import com.rivers.approval.event.FlowEventBus;
import com.rivers.approval.event.FlowEventMetadata;
import com.rivers.approval.event.InstanceStartedEvent;
import com.rivers.approval.repository.FlowDefinitionRepository;
import com.rivers.approval.repository.FlowInstanceRepository;
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
import java.util.UUID;

/**
 * 流程服务 — 对外暴露的流程操作入口。
 *
 * <p>核心职责：
 * <ul>
 *   <li>发起流程：校验定义 → 创建实例 → 发布 InstanceStartedEvent → 引擎自动推进</li>
 *   <li>查询流程：实例详情、待办列表、历史轨迹</li>
 * </ul>
 *
 * <p>发起流程后无需手动调用 FlowExecutor，事件驱动机制会自动接管后续所有推进。
 */
@Service
@Slf4j
public class FlowService {

    private final FlowDefinitionRepository defRepo;
    private final FlowInstanceRepository instanceRepo;
    private final FlowEventBus eventBus;
    private final ObjectMapper objectMapper;

    public FlowService(FlowDefinitionRepository defRepo,
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
                            .startTime(LocalDateTime.now())
                            .createUser(initiator)
                            .updateUser(initiator)
                            .build();
                    return instanceRepo.save(instance);
                })
                .map(instance -> {
                    log.info("[FlowService] 流程发起成功 instanceId={}, instanceNo={}",
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
                            LocalDateTime.now(), req.getOperator());
                })
                .thenReturn(ResultVO.ok());
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("[FlowService] JSON 序列化失败", e);
            return "{}";
        }
    }
}
