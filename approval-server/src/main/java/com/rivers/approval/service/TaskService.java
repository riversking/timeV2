package com.rivers.approval.service;

import com.rivers.approval.entity.FlowTask;
import com.rivers.approval.event.FlowEventBus;
import com.rivers.approval.event.FlowEventMetadata;
import com.rivers.approval.event.TaskCompletedEvent;
import com.rivers.approval.repository.FlowTaskRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Objects;
import java.util.UUID;

@Service
@Slf4j
public class TaskService {


    public static final String MISSION_NOT_EXIT = "任务不存在: ";
    public static final String PENDING = "PENDING";
    public static final String CLAIMED = "CLAIMED";
    private final FlowTaskRepository taskRepo;
    private final FlowEventBus eventBus;

    public TaskService(FlowTaskRepository taskRepo, FlowEventBus eventBus) {
        this.taskRepo = taskRepo;
        this.eventBus = eventBus;
    }

    // ==================== 查询 ====================

    /**
     * 查某人的待办任务（已认领，未完成）。
     */
    public Flux<FlowTask> listTodo(String userId, int page, int size) {
        var offset = (page - 1) * size;
        return taskRepo.findTodoByUserWithPage(userId, offset, size);
    }

    /**
     * 查某人的待认领任务池（PENDING 状态且候选人群包含该用户）。
     */
    public Flux<FlowTask> listClaimable(String userId, int page, int size) {
        var offset = (page - 1) * size;
        return taskRepo.findClaimableByUserWithPage(userId, offset, size);
    }

    /**
     * 统计某人待办 + 待认领总数。
     */
    public Mono<Long> countPending(String userId) {
        return taskRepo.countPendingByUser(userId);
    }

    /**
     * 查某实例下所有任务。
     */
    public Flux<FlowTask> listByInstance(Long instanceId) {
        return taskRepo.findByInstanceId(instanceId);
    }

    /**
     * 根据任务编号精确查询。
     */
    public Mono<FlowTask> getByTaskNo(String taskNo) {
        return taskRepo.findByTaskNo(taskNo);
    }

    // ==================== 任务操作 ====================

    /**
     * 认领任务。
     *
     * <p>CAS 实现：SQL 中有 {@code WHERE status = 'PENDING'}
     * 和 {@code JSON_CONTAINS(candidate_users, ...)} 双重校验，
     * 多人并发认领同一条任务时只有一人成功（受影响行数 = 1）。
     *
     * @param taskNo 任务编号
     * @param userId 认领人工号
     * @return 认领成功后的任务；失败抛出异常
     */
    @Transactional(rollbackFor = Exception.class)
    public Mono<FlowTask> claim(String taskNo, String userId) {
        log.info("[TaskService] 认领任务 taskNo={}, userId={}", taskNo, userId);
        return taskRepo.findByTaskNo(taskNo)
                .switchIfEmpty(Mono.error(
                        new IllegalArgumentException(MISSION_NOT_EXIT + taskNo)))
                .flatMap(task -> {
                    if (!PENDING.equals(task.getStatus())) {
                        return Mono.error(
                                new IllegalStateException("任务状态不允许认领: " + task.getStatus()));
                    }
                    return taskRepo.claim(task.getId(), userId)
                            .filter(rows -> rows > 0)
                            .switchIfEmpty(Mono.error(
                                    new IllegalStateException("认领失败：任务已被他人认领或候选人权限不足")))
                            .flatMap(rows -> taskRepo.findByTaskNo(taskNo))
                            .doOnNext(t -> log.info("[TaskService] 认领成功 taskNo={}, userId={}",
                                    t.getTaskNo(), t.getClaimedBy()));
                });
    }

    /**
     * 完成任务。
     *
     * <p>CAS 实现：SQL 中 {@code WHERE claimed_by = :userId AND status = 'CLAIMED'}，
     * 确保只有认领人本人能完成，且不可能重复完成。
     *
     * <p>完成后发布 {@link TaskCompletedEvent}，FlowExecutor 订阅该事件：
     * <ol>
     *   <li>标记对应 node_instance 为 COMPLETED</li>
     *   <li>发布 NodeCompletedEvent 推进到后续节点</li>
     * </ol>
     *
     * @param taskNo  任务编号
     * @param result  处理结果（APPROVED / REJECTED）
     * @param comment 审批意见
     * @param userId  操作人工号
     * @return 完成后的任务
     */
    @Transactional(rollbackFor = Exception.class)
    public Mono<FlowTask> complete(String taskNo, String result, String comment, String userId) {
        log.info("[TaskService] 完成任务 taskNo={}, result={}, userId={}", taskNo, result, userId);
        return taskRepo.findByTaskNo(taskNo)
                .switchIfEmpty(Mono.error(
                        new IllegalArgumentException(MISSION_NOT_EXIT + taskNo)))
                .flatMap(task -> {
                    if (!CLAIMED.equals(task.getStatus())) {
                        return Mono.error(
                                new IllegalStateException("任务状态不允许完成: " + task.getStatus()));
                    }
                    if (!userId.equals(task.getClaimedBy())) {
                        return Mono.error(
                                new IllegalStateException("只有认领人才能完成任务"));
                    }
                    var actualResult = result != null ? result : "APPROVED";
                    var actualComment = comment != null ? comment : "";
                    return taskRepo.complete(task.getId(), actualResult, actualComment, userId)
                            .filter(rows -> rows > 0)
                            .switchIfEmpty(Mono.error(
                                    new IllegalStateException("任务完成失败，可能已被取消")))
                            .flatMap(rows -> taskRepo.findByTaskNo(taskNo))
                            .flatMap(completed -> publishTaskCompletedEvent(completed, actualResult, actualComment, userId)
                                    .thenReturn(completed));
                });
    }

    /**
     * 取消任务。
     * 仅 PENDING / CLAIMED 状态可取消。
     */
    @Transactional(rollbackFor = Exception.class)
    public Mono<Void> cancel(String taskNo, String operator) {
        log.info("[TaskService] 取消任务 taskNo={}, operator={}", taskNo, operator);

        return taskRepo.findByTaskNo(taskNo)
                .switchIfEmpty(Mono.<FlowTask>error(
                        new IllegalArgumentException(MISSION_NOT_EXIT + taskNo)))
                .flatMap(task -> {
                    if (!PENDING.equals(task.getStatus())
                            && !CLAIMED.equals(task.getStatus())) {
                        return Mono.<FlowTask>error(
                                new IllegalStateException("任务状态不允许取消: " + task.getStatus()));
                    }
                    return taskRepo.cancel(task.getId(), operator)
                            .filter(rows -> rows > 0)
                            .switchIfEmpty(Mono.<Integer>error(
                                    new IllegalStateException("取消失败")))
                            .then(Mono.just(task));
                })
                .doOnSuccess(t -> log.info("[TaskService] 任务已取消 taskNo={}", taskNo))
                .then();
    }

    /**
     * 转交任务。
     *
     * <p>两步原子操作：
     * <ol>
     *   <li>原任务标记 TRANSFERRED，记录 prev_task_id</li>
     *   <li>新建一条 PENDING 任务，assignee 为空、candidateUsers = [targetUser]</li>
     * </ol>
     *
     * @param taskNo     原任务编号
     * @param targetUser 转交目标人工号
     * @param operator   操作人（当前认领人）
     * @return 新建的任务
     */
    @Transactional(rollbackFor = Exception.class)
    public Mono<FlowTask> transfer(String taskNo, String targetUser, String operator) {
        log.info("[TaskService] 转交任务 taskNo={}, targetUser={}, operator={}", taskNo, targetUser, operator);

        return taskRepo.findByTaskNo(taskNo)
                .switchIfEmpty(Mono.error(
                        new IllegalArgumentException(MISSION_NOT_EXIT + taskNo)))
                .flatMap(task -> {
                    if (!CLAIMED.equals(task.getStatus())) {
                        return Mono.error(
                                new IllegalStateException("只能转交已认领的任务，当前状态: " + task.getStatus()));
                    }
                    if (!operator.equals(task.getClaimedBy())) {
                        return Mono.error(
                                new IllegalStateException("只有认领人才能转交任务"));
                    }
                    // 1. 原任务标记 TRANSFERRED
                    return taskRepo.transferOut(task.getId(), operator)
                            .filter(rows -> rows > 0)
                            .switchIfEmpty(Mono.<Integer>error(
                                    new IllegalStateException("转交失败")))
                            // 2. 新建任务
                            .flatMap(rows -> {
                                var newTask = FlowTask.builder()
                                        .instanceId(task.getInstanceId())
                                        .nodeInstanceId(task.getNodeInstanceId())
                                        .taskNo("T-" + UUID.randomUUID().toString()
                                                .replace("-", "").substring(0, 16))
                                        .taskName(task.getTaskName())
                                        .status(PENDING)
                                        .assignee(targetUser)
                                        .candidateUsers("[\"" + targetUser + "\"]")
                                        .priority(task.getPriority())
                                        .prevTaskId(task.getId())
                                        .createUser(operator)
                                        .updateUser(operator)
                                        .build();

                                return taskRepo.save(newTask);
                            });
                })
                .doOnSuccess(t -> log.info("[TaskService] 转交成功 原taskNo={}, 新taskNo={}, targetUser={}",
                        taskNo, Objects.requireNonNull(t).getTaskNo(), targetUser));
    }

    // ==================== 辅助 ====================

    /**
     * 发布 TaskCompletedEvent。
     * 需先查出 instance_no 以构建事件元数据。
     */
    private Mono<Void> publishTaskCompletedEvent(FlowTask task,
                                                 String result,
                                                 String comment,
                                                 String completedBy) {
        // instance_no 可从 task 关联的 instance 获取，但 FlowTask 实体不含此字段
        // 实际实现中通过 instanceRepo 查询或由调用方传入
        // 此处用占位逻辑，展示事件发布结构
        var meta =
                FlowEventMetadata.of(
                        task.getInstanceId(),
                        "",  // instanceNo 需额外查询，或 task 实体扩展此字段
                        "TASK_COMPLETED");
        eventBus.publish(TaskCompletedEvent.of(
                meta,
                task.getId(),
                task.getTaskNo(),
                task.getNodeInstanceId(),
                result,
                comment,
                completedBy));
        return Mono.empty();
    }
}
