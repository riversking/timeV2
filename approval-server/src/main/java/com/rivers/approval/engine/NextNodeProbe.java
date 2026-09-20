package com.rivers.approval.engine;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.exc.MismatchedInputException;
import com.rivers.approval.entity.FlowInstance;
import com.rivers.approval.model.ProcessDefinition;
import com.rivers.approval.repository.FlowDefinitionRepository;
import com.rivers.approval.repository.FlowInstanceRepository;
import com.rivers.approval.repository.FlowNodeInstanceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 下一节点候选前瞻解析器（跟踪链"下一处理人"展示用）。
 * <p>
 * 认领落库后，跟踪行需要立即展示"下一处理人"（下一环节候选人），
 * 但此时下一环节尚未激活、任务未创建。本组件只读前瞻：
 * 沿流程定义 DSL 从当前节点出发，出边唯一且直连 USER_TASK 时解析其候选人；
 * 出边不唯一（网关分流）或下一节点非 USER_TASK 时返回空串（"不确定"由调用方留空）。
 */
@Component
@Slf4j
public class NextNodeProbe {

    private static final String USER_TASK = "USER_TASK";
    private static final String CANDIDATE_EXPR = "candidateExpr";

    private final FlowInstanceRepository instanceRepo;
    private final FlowDefinitionRepository defRepo;
    private final FlowNodeInstanceRepository nodeRepo;
    private final AssigneeResolver assigneeResolver;
    private final ObjectMapper objectMapper;

    public NextNodeProbe(FlowInstanceRepository instanceRepo,
                         FlowDefinitionRepository defRepo,
                         FlowNodeInstanceRepository nodeRepo,
                         AssigneeResolver assigneeResolver,
                         ObjectMapper objectMapper) {
        this.instanceRepo = instanceRepo;
        this.defRepo = defRepo;
        this.nodeRepo = nodeRepo;
        this.assigneeResolver = assigneeResolver;
        this.objectMapper = objectMapper;
    }

    /**
     * 前瞻下一 USER_TASK 节点的处理人（逗号拼接；不确定返回空串）。
     */
    public Mono<String> probeNextHandlers(Long instanceId, Long nodeInstanceId) {
        return nodeRepo.findById(nodeInstanceId)
                .flatMap(nodeInstance -> instanceRepo.findById(instanceId)
                        .flatMap(instance -> defRepo.findById(instance.getDefinitionId())
                                .flatMap(def -> {
                                    var definition = objectMapper.readValue(
                                            def.getDefinitionJson(), ProcessDefinition.class);
                                    var successors = definition.successorsOf(nodeInstance.getNodeId());
                                    if (successors.size() != 1
                                            || !USER_TASK.equals(successors.get(0).type())) {
                                        return Mono.just("");
                                    }
                                    var config = successors.get(0).config();
                                    var expr = config.get(CANDIDATE_EXPR);
                                    return resolveHandlers(expr instanceof String s ? s : "",
                                            config, instance);
                                })))
                .defaultIfEmpty("");
    }

    /**
     * 候选人解析：candidateExpr 优先，回退静态 candidateUsers + assignee
     * （与 UserTaskHandler 的解析规则保持一致）。
     */
    private Mono<String> resolveHandlers(String expr, Map<String, Object> config,
                                         FlowInstance instance) {
        var variables = parseVariables(instance.getVariables());
        return assigneeResolver.resolve(expr, instance, variables)
                .map(resolved -> {
                    var handlers = resolved.isEmpty() ? collectHandlers(config) : resolved;
                    return String.join(",", handlers);
                });
    }

    /**
     * 静态处理人集合：candidateUsers 保序去重在前，assignee 兜底在后
     * （与 UserTaskHandler.collectHandlers 同构）。
     */
    private List<String> collectHandlers(Map<String, Object> config) {
        var handlers = new ArrayList<String>();
        var candidateUsers = (List<String>) config.getOrDefault("candidateUsers", List.of());
        if (candidateUsers != null) {
            candidateUsers.stream()
                    .filter(u -> u != null && !u.isBlank())
                    .filter(u -> !handlers.contains(u))
                    .forEach(handlers::add);
        }
        var assignee = (String) config.get("assignee");
        if (assignee != null && !assignee.isBlank() && !handlers.contains(assignee)) {
            handlers.add(assignee);
        }
        return handlers;
    }

    /**
     * 变量 JSON → Map（双重编码兼容，与 FlowExecutor.parseVariables 同构）。
     */
    private Map<String, Object> parseVariables(String json) {
        if (json == null || json.isBlank() || "null".equalsIgnoreCase(json)) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json,
                    new TypeReference<LinkedHashMap<String, Object>>() {
                    });
        } catch (MismatchedInputException _) {
            String inner = objectMapper.readValue(json, String.class);
            if (inner == null || inner.isBlank() || "null".equalsIgnoreCase(inner)) {
                return new LinkedHashMap<>();
            }
            return objectMapper.readValue(inner,
                    new TypeReference<LinkedHashMap<String, Object>>() {
                    });
        }
    }
}
