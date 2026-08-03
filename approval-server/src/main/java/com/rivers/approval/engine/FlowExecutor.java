package com.rivers.approval.engine;

import com.rivers.approval.entity.FlowDefinition;
import com.rivers.approval.entity.FlowInstance;
import com.rivers.approval.entity.FlowNodeInstance;
import com.rivers.approval.event.*;
import com.rivers.approval.handle.NodeHandlerRegistry;
import com.rivers.approval.model.EdgeDef;
import com.rivers.approval.model.NodeContext;
import com.rivers.approval.model.ProcessDefinition;
import com.rivers.approval.repository.*;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 流程引擎核心驱动器。
 *
 * <p>通过订阅 {@link FlowEventBus} 的事件流实现事件驱动推进，
 * 启动时建立三条订阅链路：
 * <ol>
 *   <li>InstanceStartedEvent  → 定位 Start 节点，创建节点实例，分发给 StartHandler</li>
 *   <li>NodeCompletedEvent    → 定位后继节点（沿 edges 推导），创建实例并分发</li>
 *   <li>TaskCompletedEvent    → 先完成对应 node_instance，再发布 NodeCompletedEvent 推进</li>
 * </ol>
 *
 * <p>引擎自身不包含业务逻辑，所有节点行为由 {@code NodeHandler} 实现，
 * 引擎只负责"找到下一个节点 → 创建节点实例 → 分发给 Handler"。
 */
@Component
@Slf4j
public class FlowExecutor {


    public static final String START = "START";
    public static final String SYSTEM = "SYSTEM";
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

    @PostConstruct
    public void init() {
        subscribeToInstanceStarted();
        subscribeToNodeCompleted();
        subscribeToTaskCompleted();
        log.info("[FlowExecutor] 三条事件订阅已就绪");
    }

    // ==================== 事件订阅 ====================

    private void subscribeToInstanceStarted() {
        eventBus.subscribeShared(InstanceStartedEvent.class)
                .flatMap(this::advanceToStartNode)
                .subscribe(
                        v -> {
                        },
                        err -> log.error("[FlowExecutor] InstanceStartedEvent 处理异常", err)
                );
    }

    private void subscribeToNodeCompleted() {
        eventBus.subscribeShared(NodeCompletedEvent.class)
                .flatMap(this::advanceToNextNodes)
                .subscribe(
                        v -> {
                        },
                        err -> log.error("[FlowExecutor] NodeCompletedEvent 处理异常", err)
                );
    }

    private void subscribeToTaskCompleted() {
        eventBus.subscribeShared(TaskCompletedEvent.class)
                .flatMap(this::onTaskCompleted)
                .subscribe(
                        v -> {
                        },
                        err -> log.error("[FlowExecutor] TaskCompletedEvent 处理异常", err)
                );
    }

    // ==================== InstanceStarted → Start ====================

    /**
     * 修复要点：用 flatMapMany 铺平内层，用 .then() 收拢为 Mono&lt;Void&gt;
     */
    private Mono<Void> advanceToStartNode(InstanceStartedEvent event) {
        log.info("[FlowExecutor] 收到流程发起事件 instanceId={}, definitionKey={}",
                event.instanceId(), event.definitionKey());
        return defRepo.findById(event.definitionId())
                .flatMap(def -> createAndAdvanceStart(def, event))
                .then()   // ← 关键修复：Mono<FlowInstance> → Mono<Void>
                .onErrorResume(err -> {
                    log.error("[FlowExecutor] 推进到 Start 节点失败 instanceId={}", event.instanceId(), err);
                    return Mono.empty();
                });
    }

    /**
     * 解析定义 → 找到 Start → 创建节点实例 → 分发 Handler
     */
    private Mono<FlowInstance> createAndAdvanceStart(FlowDefinition def, InstanceStartedEvent event) {

        var definition = parseDefinition(def.getDefinitionJson());
        var startNode = definition.nodes().stream()
                .filter(n -> START.equals(n.type()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("流程定义中缺少 START 节点"));
        var nodeInstance = FlowNodeInstance.builder()
                .instanceId(event.instanceId())
                .nodeId(startNode.id())
                .nodeName(startNode.name())
                .nodeType(START)
                .status("ACTIVE")
                .startTime(LocalDateTime.now(ZoneId.systemDefault()))
                .createUser(SYSTEM)
                .updateUser(SYSTEM)
                .build();
        // 修复: 用 flatMap + then() 保障内层 Mono<Void> 不退化
        // 返回值 FlowInstance 为后续链使用（虽然此处不需要，保持类型完整）
        return instanceRepo.findById(event.instanceId())
                .flatMap(instance ->
                        nodeRepo.save(nodeInstance)
                                .flatMap(ni -> {
                                    var ctx = buildContext(instance, definition, ni, event.variables());
                                    return handlerRegistry.get(START).handle(ctx);
                                })
                                // handle 返回 Mono<Void>，flatMap 到 outer 也是 Mono<Void>
                                // 我们需要返回 Mono<FlowInstance>，所以先 handle 再 thenReturn instance
                                .thenReturn(instance)
                );
    }

    // ==================== NodeCompleted → Next ====================

    /**
     * 修复要点：Flux.flatMap(...).then() + .then(Mono<Void>) 链中
     * 显式声明下游类型，避免 .then(someMono) 的泛型推断歧义。
     */
    private Mono<Void> advanceToNextNodes(NodeCompletedEvent event) {
        if ("END".equals(event.nodeType())) {
            return Mono.empty();
        }
        return loadInstanceById(event.instanceId())
                .flatMap(instance -> defRepo.findById(instance.getDefinitionId())
                        .flatMap(def -> {
                            var definition = parseDefinition(def.getDefinitionJson());
                            var allEdges = definition.edgesFrom(event.nodeId());

                            var effectiveEdges = event.targetNodeId() != null
                                    ? allEdges.stream()
                                    .filter(e -> e.target().equals(event.targetNodeId()))
                                    .toList()
                                    : allEdges;

                            if (effectiveEdges.isEmpty()) {
                                log.warn("[FlowExecutor] 节点 {} 无出边", event.nodeId());
                                return Mono.<Void>empty();
                            }

                            var mergedVars = mergeVariables(
                                    parseVariables(instance.getVariables()),
                                    event.outputVariables());
                            // 修复: 用 thenEmpty 显式收拢为 Mono<Void>
                            // Flux.flatMap(...).then() 本身返回 Mono<Void>，但链上 then(updateXxx)
                            // 可能让编译器困惑，拆为两步：先推进节点，再更新 currentNodeIds
                            return advanceNodes(instance, definition, effectiveEdges,
                                    event.operatorId(), event.operatorName(), mergedVars);
                        }))
                .onErrorResume(err -> {
                    log.error("[FlowExecutor] 推进后继节点失败 instanceId={}, nodeId={}",
                            event.instanceId(), event.nodeId(), err);
                    return Mono.empty();
                });
    }

    /**
     * 推进所有目标节点 → 更新 current_node_ids
     */
    private Mono<Void> advanceNodes(FlowInstance instance,
                                    ProcessDefinition definition,
                                    List<EdgeDef> edges,
                                    String operatorId, String operatorName,
                                    Map<String, Object> variables) {
        // 修复: thenEmpty 明确返回 Mono<Void>，不依赖 then(mono) 的泛型推断
        return Flux.fromIterable(edges)
                .flatMap(edge -> createAndHandleNode(instance, definition, edge,
                        operatorId, variables))
                .then()
                .thenEmpty(updateInstanceCurrentNodeIds(instance, edges));
    }

    // ==================== TaskCompleted → NodeCompleted ====================

    /**
     * 修复要点：updateNodeStatus 返回 Mono&lt;Integer&gt;，
     * .then(Mono.fromRunnable(...)) 在 Java 嵌套链中可能推断为 Mono&lt;Object&gt;，
     * 拆为 .flatMap(result -> Mono.fromRunnable(...)) 显式转换。
     */
    private Mono<Void> onTaskCompleted(TaskCompletedEvent event) {
        log.info("[FlowExecutor] 收到任务完成事件 taskId={}, nodeInstanceId={}, result={}",
                event.taskId(), event.nodeInstanceId(), event.result());
        return nodeRepo.findById(event.nodeInstanceId())
                .switchIfEmpty(Mono.error(
                        new IllegalStateException("节点实例不存在: " + event.nodeInstanceId())))
                .flatMap(nodeInstance -> {
                    var outputVars = Map.<String, Object>of(
                            "approvalResult", event.result(),
                            "approvalComment", event.comment(),
                            "approvedBy", event.completedBy());
                    // 修复: 不用 .then(Mono.fromRunnable)，改用 .flatMap(ignored -> ...)
                    // .then(Mono<T>) 在某些编译器下 T 推断为 Object，
                    // 而 .flatMap(ignored → Mono.fromRunnable) 返回 Mono<Void> 无歧义
                    return nodeRepo.updateNodeStatus(
                                    nodeInstance.getId(),
                                    "COMPLETED",
                                    toJson(outputVars),
                                    LocalDateTime.now(),
                                    event.completedBy())
                            .flatMap(rows -> publishNodeCompleted(
                                    event, nodeInstance, outputVars));
                })
                .onErrorResume(err -> {
                    log.error("[FlowExecutor] 处理 TaskCompletedEvent 失败 taskId={}", event.taskId(), err);
                    return Mono.empty();
                });
    }

    private Mono<Void> publishNodeCompleted(TaskCompletedEvent event,
                                            FlowNodeInstance nodeInstance,
                                            Map<String, Object> outputVars) {
        var meta = FlowEventMetadata.of(
                event.instanceId(), event.instanceNo(), "NODE_COMPLETED");
        var nodeCompleted = NodeCompletedEvent.of(
                meta,
                nodeInstance.getId(),
                nodeInstance.getNodeId(),
                nodeInstance.getNodeName(),
                nodeInstance.getNodeType(),
                outputVars,
                null);
        eventBus.publish(nodeCompleted);
        return Mono.empty();
    }

    // ==================== 节点创建 + Handler 分发 ====================

    private Mono<Void> createAndHandleNode(FlowInstance instance,
                                           ProcessDefinition definition,
                                           EdgeDef edge,
                                           String operatorId,
                                           Map<String, Object> variables) {
        var targetNode = definition.nodeById(edge.target()).orElseThrow(
                () -> new IllegalStateException("边指向的节点不存在: " + edge.target()));
        var nodeInstance = FlowNodeInstance.builder()
                .instanceId(instance.getId())
                .nodeId(targetNode.id())
                .nodeName(targetNode.name())
                .nodeType(targetNode.type())
                .status("ACTIVE")
                .inputVariables(toJson(variables))
                .startTime(LocalDateTime.now(ZoneId.systemDefault()))
                .createUser(operatorId)
                .updateUser(operatorId)
                .build();
        // 修复: .then(handler.handle(ctx)) 改为 .flatMap → handler.handle
        return nodeRepo.save(nodeInstance)
                .flatMap(ni -> {
                    log.info("[FlowExecutor] 节点实例已创建 nodeInstanceId={}, type={}, name={}",
                            ni.getId(), ni.getNodeType(), ni.getNodeName());
                    var ctx = buildContext(instance, definition, ni, variables);
                    return handlerRegistry.get(ni.getNodeType()).handle(ctx);
                });
    }

    // ==================== current_node_ids 更新 ====================

    private Mono<Void> updateInstanceCurrentNodeIds(
            FlowInstance instance,
            List<EdgeDef> newEdges) {
        var currentIds = parseStringList(instance.getCurrentNodeIds());
        // 只加不删——移除由调用方在 advanceToNextNodes 中传入时已过滤
        newEdges.forEach(edge -> {
            if (!currentIds.contains(edge.target())) {
                currentIds.add(edge.target());
            }
        });

        // 修复: updateCurrentNodeIds 返回 Mono<Integer>，用 .then() 转 Mono<Void>
        return instanceRepo.updateCurrentNodeIds(
                        instance.getId(), toJson(currentIds), SYSTEM)
                .then();
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
                variables,
                eventBus,
                instanceRepo,
                nodeRepo,
                taskRepo,
                historyRepo,
                ruleRepo);
    }

    // ==================== JSON / Variables 工具 ====================

    private ProcessDefinition parseDefinition(String json) {
        return objectMapper.readValue(json, ProcessDefinition.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseVariables(String variablesJson) {
        if (variablesJson == null || variablesJson.isBlank()) {
            return new HashMap<>();
        }
        return objectMapper.readValue(variablesJson, Map.class);
    }

    private Map<String, Object> mergeVariables(Map<String, Object> base,
                                               Map<String, Object> overrides) {
        if (overrides == null || overrides.isEmpty()) {
            return base;
        }
        var merged = new HashMap<>(base);
        merged.putAll(overrides);
        return merged;
    }

    private Mono<FlowInstance> loadInstanceById(Long instanceId) {
        return instanceRepo.findById(instanceId)
                .switchIfEmpty(Mono.error(
                        new IllegalStateException("流程实例不存在: " + instanceId)));
    }

    private List<String> parseStringList(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        return objectMapper.readValue(json, List.class);
    }

    private String toJson(Object obj) {
        return objectMapper.writeValueAsString(obj);
    }
}
