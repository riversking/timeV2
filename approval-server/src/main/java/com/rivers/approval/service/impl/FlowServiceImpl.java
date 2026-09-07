package com.rivers.approval.service.impl;

import com.rivers.approval.entity.FlowInstance;
import com.rivers.approval.event.FlowEventBus;
import com.rivers.approval.event.FlowEventMetadata;
import com.rivers.approval.event.InstanceStartedEvent;
import com.rivers.approval.repository.FlowDefinitionRepository;
import com.rivers.approval.repository.FlowInstanceRepository;
import com.rivers.approval.repository.FlowTaskDoneRepository;
import com.rivers.approval.repository.FlowTaskRepository;
import com.rivers.approval.service.IFlowService;
import com.rivers.core.vo.ResultVO;
import com.rivers.proto.StartProcessReq;
import com.rivers.proto.StartProcessRes;
import com.rivers.proto.TerminateInstanceReq;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

/**
 * 流程服务实现 — 流程发起与终止。
 * <p>
 * 业务失败不抛异常：统一返回 ResultVO.fail(msg)。
 */
@Service
@Slf4j
public class FlowServiceImpl implements IFlowService {

    private final FlowDefinitionRepository defRepo;
    private final FlowInstanceRepository instanceRepo;
    private final FlowTaskRepository taskRepo;
    private final FlowTaskDoneRepository taskDoneRepo;
    private final FlowEventBus eventBus;
    private final TransactionalOperator txOperator;

    public FlowServiceImpl(FlowDefinitionRepository defRepo,
                           FlowInstanceRepository instanceRepo,
                           FlowTaskRepository taskRepo,
                           FlowTaskDoneRepository taskDoneRepo,
                           FlowEventBus eventBus,
                           TransactionalOperator txOperator) {
        this.defRepo = defRepo;
        this.instanceRepo = instanceRepo;
        this.taskRepo = taskRepo;
        this.taskDoneRepo = taskDoneRepo;
        this.eventBus = eventBus;
        this.txOperator = txOperator;
    }

    /**
     * 发起流程。
     *
     * @return 创建后的流程实例
     */
    @Override
    public Mono<ResultVO<StartProcessRes>> startProcess(StartProcessReq req) {
        var initiator = req.getInitiator();
        var initiatorName = req.getInitiatorName();
        var variables = req.getVariables();
        // 1) 事务内：定义校验 + 实例落库。
        // 事件必须在事务提交后发布，否则引擎异步消费时可能读不到未提交的实例行
        Mono<FlowInstance> created = defRepo.findLatestPublishedByKey(req.getDefinitionKey())
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
                            .variables(variables.isBlank() ? null : variables)
                            .startTime(LocalDateTime.now(ZoneId.systemDefault()))
                            .createUser(initiator)
                            .updateUser(initiator)
                            .build();
                    return instanceRepo.save(instance);
                })
                .as(txOperator::transactional);
        // 2) 事务提交后：发布事件，引擎开始推进
        return created
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
                })
                .switchIfEmpty(Mono.just(ResultVO.fail(
                        "流程定义不存在或未发布: " + req.getDefinitionKey())));
    }

    /**
     * 终止流程实例（管理员操作）。
     * <p>
     * 新模型终止闭环：实例置 TERMINATED 后，未完成任务按
     * "CAS 标记 CANCELLED → 归档已办表 → 物理删除"三步冻结，
     * 全程同一事务，阻止终止后继续推进流程。
     */
    @Override
    public Mono<ResultVO<Void>> terminateProcess(TerminateInstanceReq req) {
        return instanceRepo.findById(req.getInstanceId())
                .flatMap(instance -> {
                    if (!"RUNNING".equals(instance.getStatus())) {
                        return Mono.just(ResultVO.<Void>fail("只能终止运行中的流程"));
                    }
                    return instanceRepo.updateStatus(
                                    req.getInstanceId(), "TERMINATED",
                                    LocalDateTime.now(ZoneId.systemDefault()), req.getOperator())
                            .filter(rows -> rows > 0)
                            // 冻结未完成任务：CAS 标记 → 归档已办表 → 物理删除（同事务）
                            .flatMap(_ -> taskRepo.cancelActiveByInstanceId(
                                            req.getInstanceId(), req.getOperator())
                                    .then(taskDoneRepo.archiveCancelledByInstanceId(
                                            req.getInstanceId(), req.getOperator()))
                                    .then(taskRepo.deleteCancelledByInstanceId(req.getInstanceId()))
                                    .doOnNext(ignored -> log.info(
                                            "[FlowServiceImpl] 实例已终止 instanceId={}",
                                            req.getInstanceId()))
                                    .thenReturn(ResultVO.<Void>ok()))
                            .switchIfEmpty(Mono.just(ResultVO.fail("终止失败：实例状态已变更")));
                })
                .switchIfEmpty(Mono.just(ResultVO.fail(
                        "流程实例不存在: " + req.getInstanceId())))
                .as(txOperator::transactional);
    }
}