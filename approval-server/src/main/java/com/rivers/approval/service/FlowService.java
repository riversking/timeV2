package com.rivers.approval.service;

import com.rivers.approval.entity.FlowInstance;
import com.rivers.approval.event.FlowEventBus;
import com.rivers.approval.event.FlowEventMetadata;
import com.rivers.approval.event.InstanceStartedEvent;
import com.rivers.approval.repository.FlowDefinitionRepository;
import com.rivers.approval.repository.FlowInstanceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
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
     * @param definitionKey 流程定义标识（如 leave-apply）
     * @param initiator     发起人工号
     * @param initiatorName 发起人姓名
     * @param title         实例标题
     * @param businessKey   关联业务主键（可选）
     * @param variables     初始流程变量
     * @return 创建后的流程实例
     */
    @Transactional(rollbackFor = Exception.class)
    public Mono<FlowInstance> startProcess(String definitionKey,
                                           String initiator,
                                           String initiatorName,
                                           String title,
                                           String businessKey,
                                           Map<String, Object> variables) {
        // 1. 查找已发布的最新定义
        return defRepo.findLatestPublishedByKey(definitionKey)
                .switchIfEmpty(Mono.error(
                        new IllegalArgumentException("流程定义不存在或未发布: " + definitionKey)))
                .flatMap(def -> {
                    // 2. 创建流程实例
                    var instanceNo = "PI-" + UUID.randomUUID().toString()
                            .replace("-", "").substring(0, 16);
                    var variablesJson = toJson(variables);
                    var instance = FlowInstance.builder()
                            .instanceNo(instanceNo)
                            .definitionId(def.getId())
                            .definitionKey(def.getDefinitionKey())
                            .definitionVersion(def.getVersion())
                            .title(title)
                            .initiator(initiator)
                            .initiatorName(initiatorName)
                            .businessKey(businessKey != null ? businessKey : "")
                            .status("RUNNING")
                            .variables(variablesJson)
                            .startTime(LocalDateTime.now(ZoneId.systemDefault()))
                            .createUser(initiator)
                            .updateUser(initiator)
                            .build();
                    return instanceRepo.save(instance)
                            .doOnNext(i -> {
                                // 3. 发布 InstanceStartedEvent，触发引擎推进
                                log.info("[FlowService] 流程发起成功 instanceId={}, instanceNo={}, definitionKey={}",
                                        i.getId(), i.getInstanceNo(), definitionKey);
                                var meta = FlowEventMetadata.of(
                                        i.getId(), i.getInstanceNo(), "INSTANCE_STARTED");
                                eventBus.publish(InstanceStartedEvent.of(
                                        meta, def.getId(), def.getDefinitionKey(), initiator));
                            });
                });
    }

    /**
     * 终止流程实例（管理员操作）。
     */
    @Transactional(rollbackFor = Exception.class)
    public Mono<Void> terminateProcess(Long instanceId, String operator) {
        return instanceRepo.findById(instanceId)
                .flatMap(instance -> {
                    if (!"RUNNING".equals(instance.getStatus())) {
                        return Mono.error(new IllegalStateException("只能终止运行中的流程"));
                    }
                    return instanceRepo.updateStatus(
                            instanceId, "TERMINATED", LocalDateTime.now(), operator);
                })
                .then();
    }

    private String toJson(Object obj) {
        return objectMapper.writeValueAsString(obj);
    }
}
