package com.rivers.approval.rule;

import com.rivers.approval.client.RuleEngineClient;
import com.rivers.approval.model.GatewayRule;
import com.rivers.proto.GatewayRuleProto;
import com.rivers.proto.ResolveAssigneesReq;
import com.rivers.proto.RouteEdgeProto;
import com.rivers.proto.RouteGatewayReq;
import com.rivers.proto.ValidateDefinitionReq;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 规则引擎门面·远程实现（{@code approval.rule-engine.mode=remote}）。
 *
 * <p>经 {@link RuleEngineClient}（@HttpExchange + nacos 服务发现）调用独立 rule-engine-server。
 * <p>失败语义（与方案一致）：
 * <ul>
 *   <li>业务失败（code!=200，如"无规则命中且无默认边"）→ Mono.error（调用方映射实例 FAILED）</li>
 *   <li>传输失败（超时/不可达）→ Mono.error（任务创建路径由 MQ 重投承接；网关路径置 FAILED）</li>
 *   <li>定义校验调用失败 → 转错误消息（创建失败，不重试）</li>
 * </ul>
 * <p>超时需覆盖首次调用的服务发现冷启动开销（实测约 1.5s），默认 3000ms。
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "approval.rule-engine.mode", havingValue = "remote")
public class RemoteRuleEngineFacade implements RuleEngineFacade {

    private static final Integer OK_CODE = 200;

    private final RuleEngineClient client;
    private final ObjectMapper objectMapper;
    private final Duration timeout;

    public RemoteRuleEngineFacade(RuleEngineClient client,
                                  ObjectMapper objectMapper,
                                  @Value("${approval.rule-engine.timeout-ms:3000}") long timeoutMs) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.timeout = Duration.ofMillis(timeoutMs);
    }

    @Override
    public Mono<List<String>> resolveAssignees(String expr, String initiator, Map<String, Object> variables) {
        var req = ResolveAssigneesReq.newBuilder()
                .setExpr(expr == null ? "" : expr)
                .setInitiator(initiator == null ? "" : initiator)
                .setVariablesJson(toJson(variables))
                .build();
        return client.resolveAssignees(req)
                .timeout(timeout)
                .map(vo -> {
                    if (!OK_CODE.equals(vo.getCode())) {
                        throw new IllegalStateException("规则引擎处理失败: " + vo.getMessage());
                    }
                    return vo.getData().getAssigneesList();
                });
    }

    @Override
    public Mono<RouteOutcome> routeGateway(String nodeId, List<GatewayRule> rules,
                                           List<EdgeSpec> edges, Map<String, Object> variables) {
        var req = RouteGatewayReq.newBuilder()
                .setNodeId(nodeId == null ? "" : nodeId)
                .setVariablesJson(toJson(variables));
        rules.forEach(rule -> req.addRules(GatewayRuleProto.newBuilder()
                .setCondition(rule.condition() == null ? "" : rule.condition())
                .setTargetNodeId(rule.targetNodeId() == null ? "" : rule.targetNodeId())
                .setPriority(rule.priority() == null ? 0 : rule.priority())
                .setOutputMappingJson(rule.outputMapping() == null ? "" : toJson(rule.outputMapping()))
                .build()));
        edges.forEach(edge -> req.addEdges(RouteEdgeProto.newBuilder()
                .setTarget(edge.target())
                .setConditionExpression(edge.conditionExpression() == null ? "" : edge.conditionExpression())
                .build()));
        return client.routeGateway(req.build())
                .timeout(timeout)
                .map(vo -> {
                    if (!OK_CODE.equals(vo.getCode())) {
                        // 业务失败（无规则命中且无默认边等）：与本地语义一致，映射为实例 FAILED
                        throw new IllegalStateException(vo.getMessage());
                    }
                    var data = vo.getData();
                    return new RouteOutcome(data.getTargetNodeId(),
                            parseMap(data.getOutputVariablesJson()));
                });
    }

    @Override
    public Mono<Optional<String>> validateDefinition(String definitionJson) {
        var req = ValidateDefinitionReq.newBuilder()
                .setDefinitionJson(definitionJson == null ? "" : definitionJson)
                .build();
        return client.validateDefinition(req)
                .timeout(timeout)
                .map(vo -> {
                    if (!OK_CODE.equals(vo.getCode())) {
                        return Optional.of("规则引擎校验失败: " + vo.getMessage());
                    }
                    var errors = vo.getData().getErrorsList();
                    return errors.isEmpty()
                            ? Optional.<String>empty()
                            : Optional.of(errors.get(0).getMessage());
                })
                .onErrorResume(e -> {
                    log.warn("[RemoteRuleEngine] 定义校验调用失败", e);
                    return Mono.just(Optional.of("规则引擎调用失败: " + e.getMessage()));
                });
    }

    /** 变量/映射 → JSON 字符串（空则空串） */
    private String toJson(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return "";
        }
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            log.warn("[RemoteRuleEngine] 变量序列化失败，按空变量传递", e);
            return "";
        }
    }

    /** JSON 字符串 → Map（空/解析失败按空映射处理） */
    private Map<String, Object> parseMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            log.warn("[RemoteRuleEngine] 输出变量解析失败，按空映射处理", e);
            return Map.of();
        }
    }
}
