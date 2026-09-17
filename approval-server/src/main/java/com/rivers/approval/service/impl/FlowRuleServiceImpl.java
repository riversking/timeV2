package com.rivers.approval.service.impl;

import com.rivers.approval.engine.ConditionEvaluator;
import com.rivers.approval.entity.FlowDefinition;
import com.rivers.approval.entity.FlowRule;
import com.rivers.approval.model.EdgeDef;
import com.rivers.approval.model.ProcessDefinition;
import com.rivers.approval.repository.FlowDefinitionRepository;
import com.rivers.approval.repository.FlowRuleRepository;
import com.rivers.approval.service.IFlowRuleService;
import com.rivers.core.vo.ResultVO;
import com.rivers.proto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.expression.ParseException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 规则服务实现 — flow_rule 流转规则的写入校验与管理查询。
 * <p>
 * 业务失败不抛异常：统一返回 ResultVO.fail(msg)。
 * create/update 执行写入时强校验（定义 DSL 可解析、节点为排他网关、SpEL 语法、
 * 目标节点属于出边集合），保证规则与引擎实际可执行条件一致。
 */
@Service
@Slf4j
public class FlowRuleServiceImpl implements IFlowRuleService {

    private static final String RULE_TYPE_CONDITION = "CONDITION";
    private static final String EXCLUSIVE_GATEWAY = "EXCLUSIVE_GATEWAY";
    private static final String RULE_NOT_FOUND = "规则不存在: ";
    private static final String FLOW_DEF_FAIL = "流程定义不存在: ";
    private static final String YYYY_MM_DD_HH_MM_SS = "yyyy-MM-dd HH:mm:ss";

    private final FlowRuleRepository ruleRepo;
    private final FlowDefinitionRepository defRepo;
    private final ConditionEvaluator evaluator;
    private final ObjectMapper objectMapper;

    public FlowRuleServiceImpl(FlowRuleRepository ruleRepo,
                               FlowDefinitionRepository defRepo,
                               ConditionEvaluator evaluator,
                               ObjectMapper objectMapper) {
        this.ruleRepo = ruleRepo;
        this.defRepo = defRepo;
        this.evaluator = evaluator;
        this.objectMapper = objectMapper;
    }

    // ==================== 写入 ====================

    @Override
    public Mono<ResultVO<Void>> create(CreateFlowRuleReq req) {
        var ruleCode = req.getRuleCode();
        if (ruleCode == null || ruleCode.isBlank()) {
            return Mono.just(ResultVO.fail("ruleCode 不能为空"));
        }
        if (req.getName() == null || req.getName().isBlank()) {
            return Mono.just(ResultVO.fail("name 不能为空"));
        }
        return defRepo.findById(req.getDefinitionId())
                .flatMap(def -> {
                    var err = validateBinding(def, req.getNodeId(), req.getRuleConfig());
                    if (err != null) {
                        return Mono.just(ResultVO.<Void>fail(err));
                    }
                    // ruleCode 全局唯一预检（含已软删行，与 DB uk_rule_code 行为一致）
                    return ruleRepo.findByCode(ruleCode)
                            .flatMap(_ -> Mono.just(ResultVO.<Void>fail("规则编码已存在: " + ruleCode)))
                            .switchIfEmpty(createNew(def, req));
                })
                .switchIfEmpty(Mono.just(ResultVO.fail(FLOW_DEF_FAIL + req.getDefinitionId())));
    }

    private Mono<ResultVO<Void>> createNew(FlowDefinition def, CreateFlowRuleReq req) {
        var loginUser = req.getLoginUser();
        var rule = FlowRule.builder()
                .ruleCode(req.getRuleCode())
                .name(req.getName())
                .description(req.getDescription())
                .ruleType(RULE_TYPE_CONDITION)
                .definitionId(def.getId())
                .nodeId(req.getNodeId())
                .ruleConfig(req.getRuleConfig())
                .priority(req.getPriority())
                .enabled(1)
                .createUser(loginUser.getUserId())
                .updateUser(loginUser.getUserId())
                .build();
        return ruleRepo.save(rule)
                .doOnNext(r -> log.info("[FlowRuleServiceImpl] 规则已创建 ruleCode={}", r.getRuleCode()))
                .map(_ -> ResultVO.<Void>ok())
                .onErrorResume(DataIntegrityViolationException.class, e -> {
                    log.warn("[FlowRuleServiceImpl] 规则编码唯一约束冲突 ruleCode={}", req.getRuleCode());
                    return Mono.just(ResultVO.<Void>fail("规则编码已存在: " + req.getRuleCode()));
                });
    }

    @Override
    public Mono<ResultVO<Void>> update(UpdateFlowRuleReq req) {
        if (req.getName() == null || req.getName().isBlank()) {
            return Mono.just(ResultVO.fail("name 不能为空"));
        }
        return ruleRepo.findActiveById(req.getId())
                .flatMap(rule -> defRepo.findById(rule.getDefinitionId())
                        .flatMap(def -> {
                            var err = validateBinding(def, req.getNodeId(), req.getRuleConfig());
                            if (err != null) {
                                return Mono.just(ResultVO.<Void>fail(err));
                            }
                            rule.setName(req.getName());
                            rule.setDescription(req.getDescription());
                            rule.setNodeId(req.getNodeId());
                            rule.setRuleConfig(req.getRuleConfig());
                            rule.setPriority(req.getPriority());
                            rule.setUpdateUser(req.getLoginUser().getUserId());
                            // R2DBC save 会回写加载到的旧值，审计时间需显式刷新
                            rule.setUpdateTime(LocalDateTime.now(ZoneId.systemDefault()));
                            return ruleRepo.save(rule)
                                    .doOnNext(r -> log.info("[FlowRuleServiceImpl] 规则已更新 ruleCode={}",
                                            r.getRuleCode()))
                                    .map(_ -> ResultVO.<Void>ok());
                        })
                        .switchIfEmpty(Mono.just(ResultVO.fail(FLOW_DEF_FAIL + rule.getDefinitionId()))))
                .switchIfEmpty(Mono.just(ResultVO.fail(RULE_NOT_FOUND + req.getId())));
    }

    @Override
    public Mono<ResultVO<Void>> delete(DeleteFlowRuleReq req) {
        return ruleRepo.findActiveById(req.getId())
                .flatMap(rule -> ruleRepo.softDelete(req.getId(), req.getLoginUser().getUserId())
                        .doOnNext(rows -> log.info("[FlowRuleServiceImpl] 规则已删除 ruleCode={}",
                                rule.getRuleCode()))
                        .map(_ -> ResultVO.<Void>ok()))
                .switchIfEmpty(Mono.just(ResultVO.fail(RULE_NOT_FOUND + req.getId())));
    }

    @Override
    public Mono<ResultVO<Void>> toggleEnabled(ToggleFlowRuleReq req) {
        var enabled = req.getEnabled();
        if (enabled != 0 && enabled != 1) {
            return Mono.just(ResultVO.fail("enabled 仅支持 0/1"));
        }
        return ruleRepo.findActiveById(req.getId())
                .flatMap(rule -> ruleRepo.toggleEnabled(req.getId(), enabled,
                                req.getLoginUser().getUserId())
                        .doOnNext(rows -> log.info(
                                "[FlowRuleServiceImpl] 规则启停 ruleCode={}, enabled={}",
                                rule.getRuleCode(), enabled))
                        .map(_ -> ResultVO.<Void>ok()))
                .switchIfEmpty(Mono.just(ResultVO.fail(RULE_NOT_FOUND + req.getId())));
    }

    // ==================== 查询 ====================

    @Override
    public Mono<ResultVO<FlowRuleRes>> get(GetFlowRuleReq req) {
        return ruleRepo.findActiveById(req.getId())
                .map(this::toRuleRes)
                .map(ResultVO::ok)
                .switchIfEmpty(Mono.just(ResultVO.fail(RULE_NOT_FOUND + req.getId())));
    }

    @Override
    public Mono<ResultVO<FlowRuleListRes>> listByDefinition(ListFlowRuleReq req) {
        var offset = (req.getCurrentPage() - 1) * req.getPageSize();
        var nodeId = req.getNodeId();
        var rules = nodeId == null || nodeId.isBlank()
                ? ruleRepo.findByDefinitionIdWithPage(req.getDefinitionId(), offset, req.getPageSize())
                : ruleRepo.findByDefinitionIdAndNodeIdWithPage(req.getDefinitionId(), nodeId,
                        offset, req.getPageSize());
        return rules.map(this::toRuleRes)
                .collectList()
                .map(list -> ResultVO.ok(FlowRuleListRes.newBuilder()
                        .addAllRules(list)
                        .build()));
    }

    // ==================== 校验 / 转换 ====================

    /**
     * 绑定校验：定义 DSL 可解析 → 节点存在且为排他网关 → ruleConfig 为 JSON 数组
     * → condition 非空且 SpEL 语法可解析 → targetNodeId 属于该网关出边目标集合
     * → outputMapping 缺省为 null 或必须是 JSON 对象。
     *
     * @return null 表示通过；否则返回失败原因
     */
    private String validateBinding(FlowDefinition def, String nodeId, String ruleConfigJson) {
        if (nodeId == null || nodeId.isBlank()) {
            return "nodeId 不能为空";
        }
        ProcessDefinition definition;
        try {
            definition = objectMapper.readValue(def.getDefinitionJson(), ProcessDefinition.class);
        } catch (Exception e) {
            return "流程定义 DSL 解析失败: " + e.getMessage();
        }
        var node = definition.nodeById(nodeId);
        if (node.isEmpty()) {
            return "节点不存在: " + nodeId;
        }
        if (!EXCLUSIVE_GATEWAY.equals(node.get().type())) {
            return "规则只能绑定排他网关节点，实际类型: " + node.get().type();
        }
        if (ruleConfigJson == null || ruleConfigJson.isBlank()) {
            return "ruleConfig 不能为空";
        }
        List<Map<String, Object>> items;
        try {
            items = objectMapper.readValue(ruleConfigJson,
                    new TypeReference<List<Map<String, Object>>>() {
                    });
        } catch (Exception e) {
            return "ruleConfig 必须是 JSON 数组: " + e.getMessage();
        }
        if (items == null || items.isEmpty()) {
            return "ruleConfig 至少包含一条规则配置";
        }
        var edgeTargets = definition.edgesFrom(nodeId).stream()
                .map(EdgeDef::target)
                .collect(Collectors.toSet());
        for (var item : items) {
            if (item == null) {
                return "ruleConfig 元素必须是 JSON 对象";
            }
            var condition = item.get("condition");
            if (!(condition instanceof String c) || c.isBlank()) {
                return "规则缺少 condition 或为空";
            }
            try {
                evaluator.validate(c);
            } catch (ParseException e) {
                return "condition SpEL 语法错误: " + c;
            }
            var targetNodeId = item.get("targetNodeId");
            if (!(targetNodeId instanceof String t) || t.isBlank()) {
                return "规则缺少 targetNodeId 或为空";
            }
            if (!edgeTargets.contains(t)) {
                return "targetNodeId 不在该网关出边目标集合中: " + t;
            }
            var outputMapping = item.get("outputMapping");
            if (outputMapping != null && !(outputMapping instanceof Map)) {
                return "outputMapping 必须是 JSON 对象";
            }
        }
        return null;
    }

    private FlowRuleRes toRuleRes(FlowRule rule) {
        return FlowRuleRes.newBuilder()
                .setId(rule.getId())
                .setRuleCode(rule.getRuleCode())
                .setName(rule.getName())
                .setDescription(rule.getDescription())
                .setRuleType(rule.getRuleType())
                .setDefinitionId(rule.getDefinitionId() == null ? 0L : rule.getDefinitionId())
                .setNodeId(rule.getNodeId())
                .setRuleConfig(rule.getRuleConfig())
                .setPriority(rule.getPriority() == null ? 0 : rule.getPriority())
                .setEnabled(rule.getEnabled() == null ? 0 : rule.getEnabled())
                .setCreateUser(rule.getCreateUser())
                .setCreateTime(Optional.ofNullable(rule.getCreateTime())
                        .map(t -> DateTimeFormatter.ofPattern(YYYY_MM_DD_HH_MM_SS).format(t))
                        .orElse(""))
                .setUpdateUser(rule.getUpdateUser())
                .setUpdateTime(Optional.ofNullable(rule.getUpdateTime())
                        .map(t -> DateTimeFormatter.ofPattern(YYYY_MM_DD_HH_MM_SS).format(t))
                        .orElse(""))
                .build();
    }
}
