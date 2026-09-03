package com.rivers.approval.handle;

import com.rivers.approval.entity.FlowInstance;
import com.rivers.approval.entity.FlowNodeInstance;
import com.rivers.approval.entity.FlowTask;
import com.rivers.approval.event.FlowEventMetadata;
import com.rivers.approval.event.TaskCreatedEvent;
import com.rivers.approval.model.NodeContext;
import com.rivers.approval.model.NodeDef;
import com.rivers.approval.repository.FlowTaskRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

/**
 * USER_TASK 节点处理器。
 * <p>
 * 到达该节点时：
 * 1. 合并指定处理人与候选人（去重），每人创建一条 FlowTask 记录（PENDING），
 * 待办表无候选人 JSON 字段，多候选以"每人一行"表达
 * 2. 每条任务行发布 TaskCreatedEvent（外部系统可监听做通知推送）
 * 3. 不发布 NodeCompletedEvent —— 任务需要人工完成，由 TaskService.complete() 触达
 * <p>
 * 幂等性：重复建行会触发 uk_node_assignee 唯一约束，单行跳过，
 * 保证 RabbitMQ 事件重投不会产生重复待办。
 */
@Component
@Slf4j
public class UserTaskHandler implements NodeHandler {

    private final FlowTaskRepository taskRepo;

    public UserTaskHandler(FlowTaskRepository taskRepo) {
        this.taskRepo = taskRepo;
    }

    @Override
    public String supportedType() {
        return "USER_TASK";
    }

    @Override
    public Mono<Void> handle(NodeContext ctx) {
        var nodeInstance = ctx.currentNode();
        var instance = ctx.instance();
        var def = ctx.definition();
        var config = def.nodeById(nodeInstance.getNodeId())
                .map(NodeDef::config)
                .orElse(Collections.emptyMap());
        // 1. 从节点配置解析指定处理人与候选人
        var assignee = (String) config.get("assignee");
        var candidateUsers = (List<String>) config.getOrDefault("candidateUsers", List.of());
        // 2. 处理人集合：候选人 + 指定人，去重；为空时兜底一行
        var handlers = new LinkedHashSet<String>();
        if (candidateUsers != null) {
            candidateUsers.stream()
                    .filter(u -> u != null && !u.isBlank())
                    .forEach(handlers::add);
        }
        if (assignee != null && !assignee.isBlank()) {
            handlers.add(assignee);
        }
        if (handlers.isEmpty()) {
            handlers.add("");
        }
        // 3. 每人一行；唯一约束冲突时单行跳过，保证引擎重投幂等
        return Flux.fromIterable(handlers)
                .concatMap(user -> createTask(instance, nodeInstance, user, ctx))
                .then();
    }

    private Mono<Void> createTask(FlowInstance instance,
                                  FlowNodeInstance nodeInstance,
                                  String user,
                                  NodeContext ctx) {
        var task = FlowTask.builder()
                .instanceId(instance.getId())
                .nodeInstanceId(nodeInstance.getId())
                .taskNo("T-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16))
                .taskName(nodeInstance.getNodeName())
                .status("PENDING")
                .assignee(user)
                .createUser("SYSTEM")
                .updateUser("SYSTEM")
                .build();
        return taskRepo.save(task)
                .onErrorResume(DataIntegrityViolationException.class, e -> {
                    log.warn("[UserTaskHandler] 重复任务行跳过 nodeInstanceId={}, assignee={}",
                            nodeInstance.getId(), user);
                    return Mono.empty();
                })
                .doOnNext(t -> {
                    log.info("[UserTaskHandler] 任务已创建 taskId={}, taskNo={}, assignee={}",
                            t.getId(), t.getTaskNo(), t.getAssignee());
                    var meta = FlowEventMetadata.of(
                            instance.getId(), instance.getInstanceNo(), "TASK_CREATED");
                    ctx.eventBus().publish(TaskCreatedEvent.of(
                            meta, t.getId(), t.getTaskNo(), nodeInstance.getId(),
                            t.getTaskName(), t.getAssignee(), List.of(t.getAssignee())));
                })
                .then();
    }
}