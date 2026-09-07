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

import java.util.*;

/**
 * USER_TASK 节点处理器。
 * <p>
 * 到达该节点时：
 * 1. 解析任务模式 taskMode：
 *    <ul>
 *      <li>CLAIM（默认）— 需领单：多人候选每人一行 PENDING，先领先得</li>
 *      <li>ANY_ONE      — 并签1人：无需领单，任一人办理即节点通过</li>
 *      <li>ALL          — 并签多人：无需领单，全部办理后节点通过</li>
 *      <li>SEQUENTIAL   — 串签所有人：无需领单，按序逐个办理</li>
 *    </ul>
 * 2. 每人创建一条 FlowTask 记录；SEQUENTIAL 仅首位 PENDING（可办理），
 *    后续顺位 WAITING（前序办理完成后由 Service 激活）
 * 3. 仅 PENDING 行发布 TaskCreatedEvent（WAITING 行办理时再感知）
 * 4. 不发布 NodeCompletedEvent —— 任务需人工办理，由 TaskService.complete() 触达
 * <p>
 * 幂等性：重复建行会触发 uk_node_assignee 唯一约束，单行跳过，
 * 保证 RabbitMQ 事件重投不会产生重复待办。
 */
@Component
@Slf4j
public class UserTaskHandler implements NodeHandler {

    private static final String CLAIM = "CLAIM";
    private static final String ANY_ONE = "ANY_ONE";
    private static final String ALL = "ALL";
    private static final String SEQUENTIAL = "SEQUENTIAL";
    private static final String PENDING = "PENDING";
    private static final String WAITING = "WAITING";

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
        // 1. 任务模式（非法值回退 CLAIM，兼容存量定义）
        var taskMode = resolveTaskMode(config);
        // 2. 处理人集合：candidateUsers 保序 + assignee 兜底，去重；为空时兜底一行
        var handlers = collectHandlers(config);
        if (handlers.isEmpty()) {
            handlers.add("");
        }
        // 3. 逐人建行：SEQUENTIAL 仅首位 PENDING，其余 WAITING；其他模式全部 PENDING
        return Flux.range(1, handlers.size())
                .concatMap(seq -> createTask(instance, nodeInstance,
                        handlers.get(seq - 1), taskMode, seq, ctx))
                .then();
    }

    private Mono<Void> createTask(FlowInstance instance,
                                  FlowNodeInstance nodeInstance,
                                  String user,
                                  String taskMode,
                                  int seq,
                                  NodeContext ctx) {
        var status = SEQUENTIAL.equals(taskMode) && seq > 1 ? WAITING : PENDING;
        var task = FlowTask.builder()
                .instanceId(instance.getId())
                .nodeInstanceId(nodeInstance.getId())
                .taskNo("T-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16))
                .taskName(nodeInstance.getNodeName())
                .status(status)
                .assignee(user)
                .taskMode(taskMode)
                .seq(seq)
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
                    if (!PENDING.equals(t.getStatus())) {
                        return;
                    }
                    log.info("[UserTaskHandler] 任务已创建 taskId={}, taskNo={}, assignee={}, mode={}",
                            t.getId(), t.getTaskNo(), t.getAssignee(), t.getTaskMode());
                    var meta = FlowEventMetadata.of(
                            instance.getId(), instance.getInstanceNo(), "TASK_CREATED");
                    ctx.eventBus().publish(TaskCreatedEvent.of(
                            meta, t.getId(), t.getTaskNo(), nodeInstance.getId(),
                            t.getTaskName(), t.getAssignee(), List.of(t.getAssignee())));
                })
                .then();
    }

    /**
     * 解析任务模式：config.taskMode ∈ {CLAIM, ANY_ONE, ALL, SEQUENTIAL}，
     * 缺省或非法值回退 CLAIM。
     */
    private String resolveTaskMode(Map<String, Object> config) {
        var raw = config.get("taskMode");
        if (raw == null) {
            return CLAIM;
        }
        var mode = String.valueOf(raw).trim().toUpperCase();
        return switch (mode) {
            case CLAIM, ANY_ONE, ALL, SEQUENTIAL -> mode;
            default -> {
                log.warn("[UserTaskHandler] 未知任务模式 {}，回退 CLAIM", mode);
                yield CLAIM;
            }
        };
    }

    /**
     * 处理人集合：candidateUsers 保序去重在前，assignee 兜底在后
     * （SEQUENTIAL 的办理顺序即此顺序）。
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
}