package com.rivers.approval.engine;

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
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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

    // ==================== 启动订阅（PostConstruct 自动注册） ====================
    @PostConstruct
    public void init() {
        subscribeToInstanceStarted();
        subscribeToNodeCompleted();
        subscribeToTaskCompleted();
        log.info("[FlowExecutor] 三条事件订阅链路已就绪");
    }

    private void subscribeToInstanceStarted() {
        eventBus.subscribeShared(InstanceStartedEvent.class)
                .flatMap(this::advanceToStartNode)
                .subscribe(
                        _ -> {
                        },
                        err -> log.error("[FlowExecutor] InstanceStarted 处理异常", err)
                );
    }

    private void subscribeToNodeCompleted() {
        eventBus.subscribeShared(NodeCompletedEvent.class)
                .flatMap(this::advanceToNextNodes)
                .subscribe(
                        _ -> {
                        },
                        err -> log.error("[FlowExecutor] NodeCompleted 处理异常", err)
                );
    }

    private void subscribeToTaskCompleted() {
        eventBus.subscribeShared(TaskCompletedEvent.class)
                .flatMap(this::resolveTaskCompletion)
                .subscribe(
                        _ -> {
                        },
                        err -> log.error("[FlowExecutor] TaskCompleted 处理异常", err)
                );
    }

    // ==================== InstanceStarted → Start ====================

    /**
     * 流程发起后：加载定义 → 解析 DSL → 找到 Start 节点 → 创建实例 → 分发 StartHandler。
     * 返回值用 .then() 收拢为 Mono&lt;Void&gt;。
     * 最终推荐写法 —— 将 definition 沿链传递，避免重复查库。
     * 上方的 advanceToStartNode 可替换为以下版本。
     */
    private Mono<Void> advanceToStartNode(InstanceStartedEvent event) {
        log.info("[FlowExecutor] → InstanceStarted instanceId={}", event.instanceId());
        // 利用 Tuple2 或 record 传递 (definition, nodeInstance)
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
                            .status("ACTIVE")
                            .startTime(LocalDateTime.now())
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
                .then()
                .onErrorResume(err -> {
                    log.error("[FlowExecutor] 推进 Start 失败 instanceId={}", event.instanceId(), err);
                    return Mono.empty();
                });
    }

    // ==================== NodeCompleted → Next ====================

    /**
     * 节点完成后：解析 DSL 出边 → 过滤指定分支（排他网关） → 并行创建下一批节点实例 → 分发 Handler。
     * 完成后更新 instance.current_node_ids。
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
                            // 排他网关：只走 targetNodeId 指定的边
                            var effectiveEdges = event.targetNodeId() != null
                                    ? allEdges.stream()
                                    .filter(e -> e.target().equals(event.targetNodeId()))
                                    .toList()
                                    : allEdges;
                            if (effectiveEdges.isEmpty()) {
                                log.warn("[FlowExecutor] 节点 {} 无出边 instanceId={}",
                                        event.nodeId(), event.instanceId());
                                return Mono.empty();
                            }
                            var mergedVars = mergeVariables(
                                    parseVariables(instance.getVariables()),
                                    event.outputVariables() != null
                                            ? event.outputVariables() : Map.of());
                            return performAdvance(instance, definition, effectiveEdges,
                                    event.operatorId(), mergedVars, event.nodeId());
                        }))
                .onErrorResume(err -> {
                    log.error("[FlowExecutor] 推进后继失败 instanceId={}, nodeId={}",
                            event.instanceId(), event.nodeId(), err);
                    return Mono.empty();
                });
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
     * 用户任务完成后：标记对应 node_instance 完成 → 发布 NodeCompletedEvent。
     * NodeCompleted 的订阅会自动接管后续推进，形成闭环。
     */
    private Mono<Void> resolveTaskCompletion(TaskCompletedEvent event) {
        log.info("[FlowExecutor] → TaskCompleted taskId={}, nodeInstanceId={}, result={}",
                event.taskId(), event.nodeInstanceId(), event.result());
        return nodeRepo.findById(event.nodeInstanceId())
                // 修复：显式声明 Mono<FlowNodeInstance>.error，杜绝 Object 泛型退化
                .switchIfEmpty(Mono.error(
                        new IllegalStateException("节点实例不存在: " + event.nodeInstanceId())))
                .flatMap(nodeInstance -> {
                    var outputVars = Map.<String, Object>of(
                            "approvalResult", event.result() != null ? event.result() : "",
                            "approvalComment", event.comment() != null ? event.comment() : "",
                            "approvedBy", event.completedBy() != null ? event.completedBy() : "");
                    return nodeRepo.updateNodeStatus(
                                    nodeInstance.getId(),
                                    "COMPLETED",
                                    toJson(outputVars),
                                    LocalDateTime.now(),
                                    event.completedBy())
                            .flatMap(rows -> {
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
                })
                .onErrorResume(err -> {
                    log.error("[FlowExecutor] TaskCompleted 处理失败 taskId={}", event.taskId(), err);
                    return Mono.empty();
                });
    }

    // ==================== 节点创建 + Handler 分发 ====================

    /**
     * 为指定边创建 FlowNodeInstance 并分发到对应的 NodeHandler。
     */
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
                .createUser(operatorId != null ? operatorId : SYSTEM)
                .updateUser(operatorId != null ? operatorId : SYSTEM)
                .build();
        return nodeRepo.save(nodeInstance)
                .flatMap(ni -> {
                    log.info("[FlowExecutor] 节点实例创建 nodeInstanceId={}, type={}, name={}",
                            ni.getId(), ni.getNodeType(), ni.getNodeName());
                    var ctx = buildContext(instance, definition, ni,
                            variables);
                    return handlerRegistry.get(ni.getNodeType()).handle(ctx);
                });
    }

    // ==================== current_node_ids 刷新 ====================

    /**
     * 移除完成的节点 ID，加入新激活的节点 ID。
     * 并行网关场景下，Fork 节点的所有子节点会同时加入。
     */
    private Mono<Void> refreshCurrentNodeIds(FlowInstance instance,
                                             String completedNodeId,
                                             List<EdgeDef> newEdges) {
        var currentIds = parseStringList(instance.getCurrentNodeIds());
        currentIds.remove(completedNodeId);
        newEdges.forEach(e -> {
            if (!currentIds.contains(e.target())) {
                currentIds.add(e.target());
            }
        });

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
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        return objectMapper.readValue(json,
                new TypeReference<LinkedHashMap<String, Object>>() {
                });
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
        return objectMapper.readValue(json, new TypeReference<List<String>>() {
        });
    }

    private Mono<FlowInstance> loadInstanceById(Long instanceId) {
        return instanceRepo.findById(instanceId)
                .switchIfEmpty(Mono.error(
                        new IllegalStateException("流程实例不存在: " + instanceId)));
    }

    private String toJson(Object obj) {
        if (obj == null) {
            return null;
        }
        return objectMapper.writeValueAsString(obj);
    }
}
