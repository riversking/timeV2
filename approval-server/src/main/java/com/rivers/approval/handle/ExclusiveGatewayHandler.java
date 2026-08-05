package com.rivers.approval.handle;

import com.rivers.approval.engine.ConditionEvaluator;
import com.rivers.approval.event.FlowEventMetadata;
import com.rivers.approval.event.NodeCompletedEvent;
import com.rivers.approval.model.NodeContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;

/**
 * 排他网关（XOR Gateway）处理器。
 *
 * <p>逻辑：
 * <ol>
 *   <li>查询 flow_rule 表中该节点关联的规则链</li>
 *   <li>用 {@link ConditionEvaluator} 按优先级评估，找到第一个命中的规则</li>
 *   <li>发布 NodeCompletedEvent，携带 targetNodeId 和 outputMapping</li>
 *   <li>FlowExecutor 依据 targetNodeId 只推进到指定的那条边</li>
 * </ol>
 *
 * <p>如果所有规则都不命中：
 * <ul>
 *   <li>走不带 conditionExpression 的默认边（DSL 中排他网关通常有一条 else 边）</li>
 *   <li>如果没有默认边，抛出异常终止流程</li>
 * </ul>
 */
@Component
@Slf4j
public class ExclusiveGatewayHandler implements NodeHandler {


    private final ConditionEvaluator evaluator;

    public ExclusiveGatewayHandler(ConditionEvaluator evaluator) {
        this.evaluator = evaluator;
    }

    @Override
    public String supportedType() {
        return "EXCLUSIVE_GATEWAY";
    }

    @Override
    public Mono<Void> handle(NodeContext ctx) {
        var instance = ctx.instance();
        var nodeInstance = ctx.currentNode();
        var definition = ctx.definition();
        var variables = ctx.variables();
        log.info("[ExclusiveGateway] 评估条件 instanceId={}, nodeId={}", instance.getId(), nodeInstance.getNodeId());
        // 1. 查询该节点的规则链
        return ctx.ruleRepo()
                .findByDefAndNode(instance.getDefinitionId(), nodeInstance.getNodeId())
                .collectList()
                .flatMap(rules -> {
                    // 2. SpEL 评估
                    var result = evaluator.evaluate(rules, variables);
                    // 3. 决定目标节点
                    String targetNodeId;
                    java.util.Map<String, Object> outputVars;

                    if (result.isPresent()) {
                        targetNodeId = result.get().targetNodeId();
                        outputVars = result.get().outputVariables() != null
                                ? result.get().outputVariables()
                                : Collections.emptyMap();
                    } else {
                        // 无规则命中 → 走默认边（conditionExpression 为 null 或空的边）
                        var defaultEdge = definition.edgesFrom(nodeInstance.getNodeId()).stream()
                                .filter(e -> e.conditionExpression() == null
                                        || e.conditionExpression().isBlank())
                                .findFirst()
                                .orElseThrow(() -> new IllegalStateException(
                                        "排他网关无规则命中且无默认边: " + nodeInstance.getNodeId()));
                        targetNodeId = defaultEdge.target();
                        outputVars = Collections.emptyMap();
                    }
                    log.info("[ExclusiveGateway] 路由结果 instanceId={}, targetNodeId={}",
                            instance.getId(), targetNodeId);
                    // 4. 完成网关节点
                    return completeGateway(ctx, targetNodeId, outputVars);
                })
                .onErrorResume(err -> {
                    log.error("[ExclusiveGateway] 处理失败 instanceId={}, nodeId={}",
                            instance.getId(), nodeInstance.getNodeId(), err);
                    // 发布失败事件的 NodeCompleted 带 null targetNodeId，由上层处理
                    return completeGateway(ctx, null, Collections.emptyMap());
                });
    }

    private Mono<Void> completeGateway(NodeContext ctx,
                                       String targetNodeId,
                                       java.util.Map<String, Object> outputVars) {
        var nodeInstance = ctx.currentNode();
        var instance = ctx.instance();
        var toJson = outputVars.isEmpty() ? null : toJson(outputVars);

        return ctx.nodeRepo().updateNodeStatus(
                        nodeInstance.getId(),
                        "COMPLETED",
                        toJson,
                        LocalDateTime.now(ZoneId.systemDefault()),
                        "SYSTEM"
                )
                .then(Mono.fromRunnable(() -> {
                    var meta = FlowEventMetadata.of(
                            instance.getId(), instance.getInstanceNo(), "NODE_COMPLETED");
                    ctx.eventBus().publish(NodeCompletedEvent.of(
                            meta,
                            nodeInstance.getId(),
                            nodeInstance.getNodeId(),
                            nodeInstance.getNodeName(),
                            nodeInstance.getNodeType(),
                            outputVars,
                            targetNodeId));
                }));
    }

    private String toJson(Object obj) {
        return new ObjectMapper().writeValueAsString(obj);
    }
}
