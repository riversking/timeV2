package com.rivers.approval.engine;

import com.rivers.approval.entity.FlowInstance;
import com.rivers.approval.entity.FlowNodeInstance;
import com.rivers.approval.event.*;
import com.rivers.approval.handle.NodeHandlerRegistry;
import com.rivers.approval.model.EdgeDef;
import com.rivers.approval.model.NodeContext;
import com.rivers.approval.model.NodeDef;
import com.rivers.approval.model.ProcessDefinition;
import com.rivers.approval.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.exc.MismatchedInputException;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 流程引擎核心驱动器。
 *
 * <p>不再自行订阅内存事件流，改由 {@code FlowEventConsumer}（RabbitMQ）桥接调用
 * 三个公开入口：
 * <ol>
 *   <li>onInstanceStarted(InstanceStartedEvent) → 定位 Start 节点，分发给 StartHandler</li>
 *   <li>onNodeCompleted(NodeCompletedEvent)     → 定位后继节点，创建实例并分发</li>
 *   <li>onTaskCompleted(TaskCompletedEvent)     → 完成对应 node_instance，再发布 NodeCompletedEvent</li>
 * </ol>
 *
 * <p>入口方法不吞错：异常传播给 RabbitMQ 容器（重试 → 死信），
 * 保证事件至少被处理一次。引擎自身不包含业务逻辑，所有节点行为由
 * {@code NodeHandler} 实现，引擎只负责"找到下一个节点 → 创建节点实例 → 分发给 Handler"。
 */
@Component
@Slf4j
public class FlowExecutor {

    private static final String START = "START";
    private static final String SYSTEM = "SYSTEM";
    private static final String PARALLEL_GATEWAY = "PARALLEL_GATEWAY";
    private static final int MAX_CAS_RETRY = 3;
    private static final String ACTIVE = "ACTIVE";

    private final NodeHandlerRegistry handlerRegistry;
    private final FlowEventBus eventBus;
    private final FlowInstanceRepository instanceRepo;
    private final FlowNodeInstanceRepository nodeRepo;
    private final FlowDefinitionRepository defRepo;
    private final FlowTaskRepository taskRepo;
    private final FlowHistoryRepository historyRepo;
    private final FlowRuleRepository ruleRepo;
    private final ObjectMapper objectMapper;

    public FlowExecutor(NodeHandlerRegistry handlerRegistry,
                        FlowEventBus eventBus,
                        FlowInstanceRepository instanceRepo,
                        FlowNodeInstanceRepository nodeRepo,
                        FlowDefinitionRepository defRepo,
                        FlowTaskRepository taskRepo,
                        FlowHistoryRepository historyRepo,
                        FlowRuleRepository ruleRepo,
                        ObjectMapper objectMapper) {
        this.handlerRegistry = handlerRegistry;
        this.eventBus = eventBus;
        this.instanceRepo = instanceRepo;
        this.nodeRepo = nodeRepo;
        this.defRepo = defRepo;
        this.taskRepo = taskRepo;
        this.historyRepo = historyRepo;
        this.ruleRepo = ruleRepo;
        this.objectMapper = objectMapper;
    }

    // ==================== RabbitMQ 消费入口（FlowEventConsumer 桥接调用） ====================

    /**
     * 引擎对外入口。错误向上传播给 RabbitMQ 容器（重试 → 死信），引擎自身不再吞错。
     */
    public Mono<Void> onInstanceStarted(InstanceStartedEvent event) {
        return advanceToStartNode(event);
    }

    public Mono<Void> onNodeCompleted(NodeCompletedEvent event) {
        return advanceToNextNodes(event);
    }

    public Mono<Void> onTaskCompleted(TaskCompletedEvent event) {
        return resolveTaskCompletion(event);
    }

    // ==================== InstanceStarted → Start ====================

    /**
     * 流程发起后：加载定义 → 解析 DSL → 找到 Start 节点 → 创建节点实例 → 分发 StartHandler。
     */
    private Mono<Void> advanceToStartNode(InstanceStartedEvent event) {
        log.info("[FlowExecutor] → InstanceStarted instanceId={}", event.instanceId());
        // record 沿链传递 (definition, nodeInstance)，避免重复查库
        record StartContext(ProcessDefinition definition, FlowNodeInstance nodeInstance) {
        }
        return defRepo.findById(event.definitionId())
                .flatMap(def -> {
                    var definition = parseDefinition(def.getDefinitionJson());
                    var startNode = definition.nodes().stream()
                            .filter(n -> START.equals(n.type()))
                            .findFirst()
                            .orElseThrow(() -> new IllegalStateException("缺少 START 节点"));
                    var ni = FlowNodeInstance.builder()
                            .instanceId(event.instanceId())
                            .nodeId(startNode.id())
                            .nodeName(startNode.name())
                            .nodeType(START)
                            .status(ACTIVE)
                            .startTime(LocalDateTime.now(ZoneId.systemDefault()))
                            .createUser(SYSTEM)
                            .updateUser(SYSTEM)
                            .build();
                    return nodeRepo.save(ni)
                            .map(saved -> new StartContext(definition, saved));
                })
                .flatMap(sc -> loadInstanceById(event.instanceId())
                        .flatMap(instance -> {
                            var ctx = buildContext(instance, sc.definition(), sc.nodeInstance(),
                                    parseVariables(instance.getVariables()));
                            return handlerRegistry.get(START).handle(ctx);
                        }))
                .then();
    }

    // ==================== NodeCompleted → Next ====================

    /**
     * 节点完成后：解析 DSL 出边 → 过滤指定分支（排他网关） → 并行创建下一批节点实例 → 分发 Handler。
     * 下游 Join 实例的预创建由 ParallelGatewayHandler 在 Fork 时完成；
     * 输出变量合并结果落库；完成后 CAS 更新 current_node_ids。
     */
    private Mono<Void> advanceToNextNodes(NodeCompletedEvent event) {
        if ("END".equals(event.nodeType())) {
            return Mono.empty();
        }
        return loadInstanceById(event.instanceId())
                .flatMap(instance -> defRepo.findById(instance.getDefinitionId())
                        .flatMap(def -> {
                            var definition = parseDefinition(def.getDefinitionJson());
                            var effectiveEdges = getEdgeDefs(event, definition);
                            if (effectiveEdges.isEmpty()) {
                                log.warn("[FlowExecutor] 节点 {} 无出边 instanceId={}",
                                        event.nodeId(), event.instanceId());
                                return Mono.empty();
                            }
                            var mergedVars = mergeVariables(
                                    parseVariables(instance.getVariables()),
                                    event.outputVariables() != null
                                            ? event.outputVariables() : Map.of());
                            // 输出变量合并结果落库，避免重启/重载后审批上下文丢失
                            var hasOutput = event.outputVariables() != null
                                    && !event.outputVariables().isEmpty();
                            var persistVars = hasOutput
                                    ? instanceRepo.updateVariables(
                                    instance.getId(), toJson(mergedVars), SYSTEM).then()
                                    : Mono.<Void>empty();
                            return persistVars
                                    .then(performAdvance(instance, definition, effectiveEdges,
                                            event.operatorId(), mergedVars, event.nodeId()));
                        }));
    }

    private static @NonNull List<EdgeDef> getEdgeDefs(NodeCompletedEvent event, ProcessDefinition definition) {
        var allEdges = definition.edgesFrom(event.nodeId());
        // 排他网关：只走 targetNodeId 指定的边
        return event.targetNodeId() != null
                ? allEdges.stream()
                .filter(e -> e.target().equals(event.targetNodeId()))
                .toList()
                : allEdges;
    }

    /**
     * 执行推进：为每条边创建节点实例 → 分发 Handler → 最后更新 current_node_ids。
     */
    private Mono<Void> performAdvance(FlowInstance instance,
                                      ProcessDefinition definition,
                                      List<EdgeDef> edges,
                                      String operatorId,
                                      Map<String, Object> variables,
                                      String completedNodeId) {
        return Flux.fromIterable(edges)
                .flatMap(edge -> createAndHandleNode(
                        instance, definition, edge,
                        operatorId, variables))
                .then()
                .thenEmpty(refreshCurrentNodeIds(instance, completedNodeId, edges));
    }

    // ==================== TaskCompleted → NodeCompleted ====================

    /**
     * 用户任务完成后：标记对应 node_instance 完成 → 合并审批结果到实例变量 → 发布 NodeCompletedEvent。
     * NodeCompletedEvent 经 RabbitMQ 回到 onNodeCompleted 入口，形成闭环。
     */
    private Mono<Void> resolveTaskCompletion(TaskCompletedEvent event) {
        log.info("[FlowExecutor] → TaskCompleted taskId={}, nodeInstanceId={}, result={}",
                event.taskId(), event.nodeInstanceId(), event.result());
        if (!event.advanceNode()) {
            log.info("[FlowExecutor] 节点办理未集齐，暂不推进 taskId={}, nodeInstanceId={}",
                    event.taskId(), event.nodeInstanceId());
            return Mono.empty();
        }
        return loadInstanceById(event.instanceId())
                .flatMap(instance -> {
                    // 终止/完成的实例不再推进，防止 terminate 后继续流转
                    if (!"RUNNING".equals(instance.getStatus())) {
                        log.warn("[FlowExecutor] 实例非运行中，忽略任务完成事件 instanceId={}, status={}",
                                instance.getId(), instance.getStatus());
                        return Mono.empty();
                    }
                    var outputVars = getOutPut(event);
                    var mergedVars = mergeVariables(parseVariables(instance.getVariables()), outputVars);
                    return nodeRepo.findById(event.nodeInstanceId())
                            .switchIfEmpty(Mono.error(
                                    new IllegalStateException("节点实例不存在: " + event.nodeInstanceId())))
                            .flatMap(nodeInstance -> nodeRepo.updateNodeStatus(
                                            nodeInstance.getId(),
                                            "COMPLETED",
                                            toJson(outputVars),
                                            LocalDateTime.now(ZoneId.systemDefault()),
                                            event.completedBy())
                                    .flatMap(rows -> {
                                        if (rows <= 0) {
                                            log.warn("[FlowExecutor] 节点实例已非 ACTIVE，跳过重复完成 nodeInstanceId={}",
                                                    nodeInstance.getId());
                                            return Mono.<FlowNodeInstance>empty();
                                        }
                                        // 审批结果合并进实例变量并落库
                                        return instanceRepo.updateVariables(
                                                        instance.getId(),
                                                        toJson(mergedVars),
                                                        event.completedBy() != null ? event.completedBy() : SYSTEM)
                                                .thenReturn(nodeInstance);
                                    }))
                            .flatMap(nodeInstance -> {
                                log.info("[FlowExecutor] 节点实例已标记完成 nodeInstanceId={}",
                                        nodeInstance.getId());
                                var meta = FlowEventMetadata.of(
                                        event.instanceId(), event.instanceNo(),
                                        "NODE_COMPLETED");
                                eventBus.publish(NodeCompletedEvent.of(
                                        meta,
                                        nodeInstance.getId(),
                                        nodeInstance.getNodeId(),
                                        nodeInstance.getNodeName(),
                                        nodeInstance.getNodeType(),
                                        outputVars,
                                        null));
                                return Mono.<Void>empty();
                            });
                });
    }

    private static @NonNull Map<String, Object> getOutPut(TaskCompletedEvent event) {
        return Map.of(
                "approvalResult", event.result() != null ? event.result() : "",
                "approvalComment", event.comment() != null ? event.comment() : "",
                "approvedBy", event.completedBy() != null ? event.completedBy() : "");
    }

    // ==================== 节点创建 + Handler 分发 ====================

    /**
     * 为指定边创建 FlowNodeInstance 并分发到对应的 NodeHandler。
     * Join 网关节点不新建实例：复用 ParallelGatewayHandler 在 Fork 时预创建的唯一实例；
     * 实例缺失（异常时序）时兜底创建。
     */
    private Mono<Void> createAndHandleNode(FlowInstance instance,
                                           ProcessDefinition definition,
                                           EdgeDef edge,
                                           String operatorId,
                                           Map<String, Object> variables) {
        var targetNode = definition.nodeById(edge.target()).orElseThrow(
                () -> new IllegalStateException("边指向的节点不存在: " + edge.target()));
        // Join 网关：所有分支复用同一个节点实例（Fork 时已由 ParallelGatewayHandler 预创建）
        if (isJoinGateway(definition, targetNode)) {
            return nodeRepo.findActiveByInstanceIdAndNodeId(instance.getId(), targetNode.id())
                    .switchIfEmpty(createJoinInstance(instance, targetNode,
                            incomingCount(definition, targetNode.id())))
                    .flatMap(ni -> dispatchToHandler(instance, definition, ni, variables));
        }
        var nodeInstance = FlowNodeInstance.builder()
                .instanceId(instance.getId())
                .nodeId(targetNode.id())
                .nodeName(targetNode.name())
                .nodeType(targetNode.type())
                .status(ACTIVE)
                .inputVariables(toJson(variables))
                .startTime(LocalDateTime.now(ZoneId.systemDefault()))
                .createUser(operatorId != null ? operatorId : SYSTEM)
                .updateUser(operatorId != null ? operatorId : SYSTEM)
                .build();
        return nodeRepo.save(nodeInstance)
                .flatMap(ni -> dispatchToHandler(instance, definition, ni, variables));
    }

    private Mono<Void> dispatchToHandler(FlowInstance instance,
                                         ProcessDefinition definition,
                                         FlowNodeInstance ni,
                                         Map<String, Object> variables) {
        log.info("[FlowExecutor] 节点实例就绪 nodeInstanceId={}, type={}, name={}",
                ni.getId(), ni.getNodeType(), ni.getNodeName());
        var ctx = buildContext(instance, definition, ni, variables);
        return handlerRegistry.get(ni.getNodeType()).handle(ctx);
    }

    // ==================== Join 实例复用 / 兜底创建 ====================

    /**
     * 兜底创建 Join 节点实例（fork_count = 入边数）。
     * 正常路径由 ParallelGatewayHandler 在 Fork 时预创建，
     * 此处仅覆盖"首条分支到达时实例缺失"的异常时序。
     */
    private Mono<FlowNodeInstance> createJoinInstance(FlowInstance instance,
                                                      NodeDef joinNode,
                                                      int forkCount) {
        var ni = FlowNodeInstance.builder()
                .instanceId(instance.getId())
                .nodeId(joinNode.id())
                .nodeName(joinNode.name())
                .nodeType(joinNode.type())
                .status(ACTIVE)
                .forkCount(forkCount)
                .joinCount(0)
                .startTime(LocalDateTime.now(ZoneId.systemDefault()))
                .createUser(SYSTEM)
                .updateUser(SYSTEM)
                .build();
        return nodeRepo.save(ni)
                .doOnNext(saved -> log.info(
                        "[FlowExecutor] 兜底创建 Join 节点实例 nodeInstanceId={}, nodeId={}, forkCount={}",
                        saved.getId(), joinNode.id(), forkCount));
    }

    private boolean isJoinGateway(ProcessDefinition definition, NodeDef node) {
        return PARALLEL_GATEWAY.equals(node.type())
                && incomingCount(definition, node.id()) >= 2;
    }

    private int incomingCount(ProcessDefinition definition, String nodeId) {
        return (int) definition.edges().stream()
                .filter(e -> e.target().equals(nodeId)).count();
    }

    // ==================== current_node_ids 刷新（乐观锁 CAS） ====================

    /**
     * 移除完成的节点 ID，加入新激活的节点 ID。
     * 并行分支并发刷新时用 version 字段 CAS + 有限重试，每次重试重读最新列表，
     * 避免读-改-写 lost update。
     */
    private Mono<Void> refreshCurrentNodeIds(FlowInstance instance,
                                             String completedNodeId,
                                             List<EdgeDef> newEdges) {
        return casUpdateNodeIds(instance.getId(), completedNodeId, newEdges, 0);
    }

    private Mono<Void> casUpdateNodeIds(Long instanceId,
                                        String completedNodeId,
                                        List<EdgeDef> newEdges,
                                        int attempt) {
        return loadInstanceById(instanceId)
                .flatMap(latest -> {
                    var currentIds = parseStringList(latest.getCurrentNodeIds());
                    currentIds.remove(completedNodeId);
                    newEdges.forEach(e -> {
                        if (!currentIds.contains(e.target())) {
                            currentIds.add(e.target());
                        }
                    });
                    return instanceRepo.updateCurrentNodeIdsCas(
                                    instanceId, toJson(currentIds),
                                    latest.getVersion(), SYSTEM)
                            .flatMap(rows -> {
                                if (rows > 0) {
                                    return Mono.<Void>empty();
                                }
                                if (attempt >= MAX_CAS_RETRY) {
                                    log.error("[FlowExecutor] current_node_ids CAS 重试耗尽 instanceId={}",
                                            instanceId);
                                    return Mono.<Void>empty();
                                }
                                log.debug("[FlowExecutor] current_node_ids CAS 冲突，重试 {}/{} instanceId={}",
                                        attempt + 1, MAX_CAS_RETRY, instanceId);
                                return casUpdateNodeIds(instanceId, completedNodeId,
                                        newEdges, attempt + 1);
                            });
                });
    }

    // ==================== NodeContext 组装 ====================

    private NodeContext buildContext(FlowInstance instance,
                                     ProcessDefinition definition,
                                     FlowNodeInstance nodeInstance,
                                     Map<String, Object> variables) {
        return new NodeContext(
                instance,
                nodeInstance,
                definition,
                variables != null ? variables : Map.of(),
                eventBus,
                instanceRepo,
                nodeRepo,
                taskRepo,
                historyRepo,
                ruleRepo);
    }

    // ==================== JSON / Variables 解析 ====================

    private ProcessDefinition parseDefinition(String json) {
        return objectMapper.readValue(json, ProcessDefinition.class);
    }

    private Map<String, Object> parseVariables(String json) {
        if (json == null || json.isBlank() || "null".equalsIgnoreCase(json)) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json,
                    new TypeReference<LinkedHashMap<String, Object>>() {
                    });
        } catch (MismatchedInputException _) {
            // 存量数据双重编码兼容：外层是 JSON 字符串，内层才是对象
            String inner = objectMapper.readValue(json, String.class);
            if (inner == null || inner.isBlank() || "null".equalsIgnoreCase(inner)) {
                return new LinkedHashMap<>();
            }
            return objectMapper.readValue(inner,
                    new TypeReference<LinkedHashMap<String, Object>>() {
                    });
        }
    }

    private Map<String, Object> mergeVariables(Map<String, Object> base,
                                               Map<String, Object> overrides) {
        if (overrides.isEmpty()) {
            return base;
        }
        var merged = new LinkedHashMap<>(base);
        merged.putAll(overrides);
        return merged;
    }

    private List<String> parseStringList(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        return objectMapper.readValue(json, new TypeReference<>() {
        });
    }

    private Mono<FlowInstance> loadInstanceById(Long instanceId) {
        return instanceRepo.findById(instanceId)
                .switchIfEmpty(Mono.error(
                        new IllegalStateException("流程实例不存在: " + instanceId)));
    }

    private String toJson(Object obj) {
        if (obj == null) {
            return "";
        }
        return objectMapper.writeValueAsString(obj);
    }
}