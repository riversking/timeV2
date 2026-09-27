package com.rivers.ruleengine.controller;

import com.rivers.core.vo.ResultVO;
import com.rivers.proto.GatewayRuleProto;
import com.rivers.proto.ResolveAssigneesReq;
import com.rivers.proto.ResolveAssigneesRes;
import com.rivers.proto.RouteGatewayReq;
import com.rivers.proto.RouteGatewayRes;
import com.rivers.proto.ValidateDefinitionReq;
import com.rivers.proto.ValidateDefinitionRes;
import com.rivers.proto.ValidateRuleError;
import com.rivers.ruleengine.engine.AssigneeResolver;
import com.rivers.ruleengine.engine.DefinitionRuleValidator;
import com.rivers.ruleengine.engine.GatewayRouter;
import com.rivers.ruleengine.model.GatewayRule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * 规则引擎 HTTP API（approval-server 经 {@code @HttpExchange} 声明式客户端调用）。
 *
 * <p>三个能力（皆为无状态纯计算，可横向扩容）：
 * <ul>
 *   <li>POST /rule/resolveAssignees — 审批人表达式解析（$leader 游标 / $leaderMax / 变量通道 / 兜底查询 / 字面保留）</li>
 *   <li>POST /rule/routeGateway — 排他网关路由（多出边三级降级；单出边直通由审批侧本地处理，不调本接口）</li>
 *   <li>POST /rule/validateDefinition — 定义创建时内嵌规则校验（首个错误即返回）</li>
 * </ul>
 *
 * <p>失败语义：业务失败返回 {@code ResultVO.fail(msg)}（HTTP 200）——approval 侧对路由失败
 * 映射为实例 FAILED（与目标守护语义一致）；传输层失败（超时/不可达）由调用方重试语义承接。
 * <p>流程变量经 variablesJson 字段以 JSON 字符串传递（动态结构，不建模 proto）。
 */
@RestController
@RequestMapping("/rule")
@Slf4j
public class RuleEngineController {

    private final AssigneeResolver assigneeResolver;
    private final GatewayRouter gatewayRouter;
    private final DefinitionRuleValidator definitionRuleValidator;
    private final ObjectMapper objectMapper;

    public RuleEngineController(AssigneeResolver assigneeResolver,
                                GatewayRouter gatewayRouter,
                                DefinitionRuleValidator definitionRuleValidator,
                                ObjectMapper objectMapper) {
        this.assigneeResolver = assigneeResolver;
        this.gatewayRouter = gatewayRouter;
        this.definitionRuleValidator = definitionRuleValidator;
        this.objectMapper = objectMapper;
    }

    /**
     * 审批人表达式解析：返回保序去重处理人列表（未解析 token 保留字面原文）。
     */
    @PostMapping("/resolveAssignees")
    public Mono<ResultVO<ResolveAssigneesRes>> resolveAssignees(@RequestBody ResolveAssigneesReq req) {
        var variables = parseVariables(req.getVariablesJson());
        return assigneeResolver.resolve(req.getExpr(), req.getInitiator(), variables)
                .map(assignees -> ResultVO.ok(ResolveAssigneesRes.newBuilder()
                        .addAllAssignees(assignees)
                        .build()));
    }

    /**
     * 排他网关路由（多出边三级降级）：内嵌规则 → 边条件 → 默认边。
     * 业务失败（无规则命中且无默认边 / SpEL 求值异常）→ ResultVO.fail。
     */
    @PostMapping("/routeGateway")
    public Mono<ResultVO<RouteGatewayRes>> routeGateway(@RequestBody RouteGatewayReq req) {
        var variables = parseVariables(req.getVariablesJson());
        var rules = req.getRulesList().stream().map(this::toRule).toList();
        var edges = req.getEdgesList().stream()
                .map(e -> new GatewayRouter.Edge(e.getTarget(), e.getConditionExpression()))
                .toList();
        try {
            var route = gatewayRouter.route(req.getNodeId(), rules, edges, variables);
            return Mono.just(ResultVO.ok(RouteGatewayRes.newBuilder()
                    .setTargetNodeId(route.targetNodeId())
                    .setOutputVariablesJson(toJson(route.outputVariables()))
                    .setHitSource(route.hitSource())
                    .build()));
        } catch (RuntimeException e) {
            // 业务失败：审批侧映射为实例 FAILED（与目标守护/求值失败语义一致）
            log.warn("[RuleEngine] 网关路由失败 nodeId={}, cause={}", req.getNodeId(), e.toString());
            return Mono.just(ResultVO.fail(message(e)));
        }
    }

    /**
     * 定义规则校验：JSON 可解析 + 排他网关内嵌规则逐条校验（首个错误即返回，无错误则 errors 为空）。
     */
    @PostMapping("/validateDefinition")
    public Mono<ResultVO<ValidateDefinitionRes>> validateDefinition(@RequestBody ValidateDefinitionReq req) {
        var definitionJson = req.getDefinitionJson();
        log.info("[RuleEngine] validateDefinition 入参 length={}, head={}",
                definitionJson == null ? -1 : definitionJson.length(),
                definitionJson == null ? "" : definitionJson.substring(0, Math.min(120, definitionJson.length())));
        var error = definitionRuleValidator.validate(definitionJson);
        log.info("[RuleEngine] validateDefinition 出参 hasError={}", error.isPresent());
        var builder = ValidateDefinitionRes.newBuilder();
        error.ifPresent(err -> builder.addErrors(ValidateRuleError.newBuilder()
                .setNodeId(err.nodeId() == null ? "" : err.nodeId())
                .setRuleIndex(err.ruleIndex())
                .setMessage(err.message())
                .build()));
        return Mono.just(ResultVO.ok(builder.build()));
    }

    /** 业务失败的 fail 消息（保证非空，审批侧原样记录/展示） */
    private String message(RuntimeException e) {
        return e.getMessage() != null ? e.getMessage() : e.toString();
    }

    /** 流程变量 JSON → Map（解析失败按空变量处理：表达式 token 保留字面，不中断链路） */
    private Map<String, Object> parseVariables(String variablesJson) {
        if (variablesJson == null || variablesJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(variablesJson, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            log.warn("[RuleEngine] 流程变量 JSON 解析失败，按空变量处理", e);
            return Map.of();
        }
    }

    /** proto 规则 → 领域模型（outputMappingJson 解析失败按 null 忽略该映射） */
    private GatewayRule toRule(GatewayRuleProto rule) {
        Map<String, Object> outputMapping = null;
        if (rule.getOutputMappingJson() != null && !rule.getOutputMappingJson().isBlank()) {
            try {
                outputMapping = objectMapper.readValue(rule.getOutputMappingJson(),
                        new TypeReference<Map<String, Object>>() {
                        });
            } catch (Exception e) {
                log.warn("[RuleEngine] 网关规则 outputMapping 解析失败，忽略该映射", e);
            }
        }
        return new GatewayRule(rule.getCondition(), rule.getTargetNodeId(),
                rule.getPriority(), outputMapping);
    }

    /** 输出变量 → JSON 字符串（空则空串） */
    private String toJson(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return "";
        }
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            log.warn("[RuleEngine] 输出变量序列化失败", e);
            return "";
        }
    }
}
