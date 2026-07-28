package com.rivers.approval.handle;

import com.rivers.approval.entity.FlowTask;
import com.rivers.approval.event.FlowEventMetadata;
import com.rivers.approval.event.TaskCreatedEvent;
import com.rivers.approval.model.NodeContext;
import com.rivers.approval.model.NodeDef;
import com.rivers.approval.repository.FlowTaskRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * USER_TASK 节点处理器。
 * <p>
 * 到达该节点时：
 * 1. 创建一条 FlowTask 记录（PENDING 状态）
 * 2. 发布 TaskCreatedEvent（外部系统可监听做通知推送）
 * 3. 不发布 NodeCompletedEvent —— 任务需要人工完成，由 TaskService.complete() 触达
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
        // 1. 从节点配置解析候选人和指定处理人
        var assignee = (String) config.get("assignee");        // 指定处理人
        var candidateUsers = (List<String>) config.getOrDefault("candidateUsers", List.of());
        // 2. 构建并存储任务
        var task = FlowTask.builder()
                .instanceId(instance.getId())
                .nodeInstanceId(nodeInstance.getId())
                .taskNo("T-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16))
                .taskName(nodeInstance.getNodeName())
                .status("PENDING")
                .assignee(assignee != null ? assignee : "")
                .candidateUsers(toJson(candidateUsers))
                .createUser("SYSTEM")
                .updateUser("SYSTEM")
                .build();
        return taskRepo.save(task)
                .doOnNext(t -> log.info("[UserTaskHandler] 任务已创建 taskId={}, taskNo={}, assignee={}",
                        t.getId(), t.getTaskNo(), t.getAssignee()))
                .then(Mono.fromRunnable(() -> {
                    var meta = FlowEventMetadata.of(
                            instance.getId(), instance.getInstanceNo(), "TASK_CREATED");
                    ctx.eventBus().publish(TaskCreatedEvent.of(
                            meta, task.getId(), task.getTaskNo(), nodeInstance.getId(),
                            task.getTaskName(), assignee, candidateUsers));
                }));
    }

    /**
     * 简单 JSON List 序列化
     */
    private String toJson(List<String> list) {
        if (list == null || list.isEmpty()) {
            return "[]";
        }
        var sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append("\"").append(list.get(i).replace("\"", "\\\"")).append("\"");
        }
        sb.append("]");
        return sb.toString();
    }
}
