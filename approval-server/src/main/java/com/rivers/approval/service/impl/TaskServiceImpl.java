package com.rivers.approval.service.impl;

import com.rivers.approval.entity.FlowTask;
import com.rivers.approval.event.FlowEventBus;
import com.rivers.approval.event.FlowEventMetadata;
import com.rivers.approval.event.TaskCompletedEvent;
import com.rivers.approval.repository.FlowTaskDoneRepository;
import com.rivers.approval.repository.FlowTaskRepository;
import com.rivers.approval.service.ITaskService;
import com.rivers.core.vo.ResultVO;
import com.rivers.proto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 任务服务实现。
 * <p>
 * 新模型约定：
 * <ul>
 *   <li>待办表（flow_task）只存在 PENDING / CLAIMED 两种活跃状态，任务终态物理删除</li>
 *   <li>终态快照（COMPLETED / TRANSFERRED / CANCELLED）统一归档到已办表（flow_task_done）</li>
 *   <li>所有写操作遵循"CAS 标记 → 归档 → 删除"三步，同一事务内完成</li>
 *   <li>业务失败不抛异常：统一返回 ResultVO.fail(msg)</li>
 * </ul>
 */
@Service
@Slf4j
public class TaskServiceImpl implements ITaskService {

    private static final String CLAIMED = "CLAIMED";
    private static final String PENDING = "PENDING";
    private static final String COMPLETED = "COMPLETED";
    private static final String CANCELLED = "CANCELLED";
    private static final String TRANSFERRED = "TRANSFERRED";
    private static final String NO_TASK = "任务不存在";
    private static final String YYYY_MM_DD_HH_MM_SS = "yyyy-MM-dd HH:mm:ss";

    private final FlowTaskRepository taskRepo;
    private final FlowTaskDoneRepository taskDoneRepo;
    private final FlowEventBus eventBus;
    private final TransactionalOperator txOperator;

    public TaskServiceImpl(FlowTaskRepository taskRepo, FlowTaskDoneRepository taskDoneRepo,
                           FlowEventBus eventBus, TransactionalOperator txOperator) {
        this.taskRepo = taskRepo;
        this.taskDoneRepo = taskDoneRepo;
        this.eventBus = eventBus;
        this.txOperator = txOperator;
    }

    // ==================== 查询 ====================

    @Override
    public Mono<ResultVO<TaskListRes>> listTodo(ListTaskReq req) {
        var offset = (req.getCurrentPage() - 1) * req.getPageSize();
        var loginUser = req.getLoginUser();
        var userId = loginUser.getUserId();
        return taskRepo.findTodoByUserWithPage(userId, offset, req.getPageSize())
                .map(this::toTaskRes)
                .collectList()
                .map(list -> ResultVO.ok(
                        TaskListRes.newBuilder()
                                .addAllTasks(list)
                                .build()));
    }

    @Override
    public Mono<ResultVO<TaskListRes>> listClaimable(ListTaskReq req) {
        var offset = (req.getCurrentPage() - 1) * req.getPageSize();
        var loginUser = req.getLoginUser();
        var userId = loginUser.getUserId();
        return taskRepo.findClaimableByUserWithPage(userId, offset, req.getPageSize())
                .map(this::toTaskRes)
                .collectList()
                .map(list -> ResultVO.ok(
                        TaskListRes.newBuilder()
                                .addAllTasks(list)
                                .build()));
    }

    @Override
    public Mono<ResultVO<FlowTaskRes>> getByTaskNo(TaskNoReq req) {
        return taskRepo.findByTaskNo(req.getTaskNo())
                .map(this::toTaskRes)
                .map(ResultVO::ok)
                .defaultIfEmpty(ResultVO.fail(NO_TASK + ": " + req.getTaskNo()));
    }

    // ==================== 操作 ====================

    @Override
    public Mono<ResultVO<Void>> claim(ClaimTaskReq req) {
        var userId = Optional.of(req.getUserId())
                .filter(u -> !u.isBlank())
                .orElseGet(() -> Optional.of(req.getLoginUser())
                        .map(LoginUser::getUserId)
                        .orElse(null));
        log.info("[TaskServiceImpl] 认领任务 taskNo={}, userId={}", req.getTaskNo(), userId);
        if (userId.isBlank()) {
            return Mono.just(ResultVO.<Void>fail("缺少认领人 userId"));
        }
        Mono<ResultVO<Void>> claimed = taskRepo.findByTaskNo(req.getTaskNo())
                .flatMap(task -> {
                    if (!PENDING.equals(task.getStatus())) {
                        return Mono.just(ResultVO.<Void>fail("任务状态不允许认领: " + task.getStatus()));
                    }
                    if (!Objects.equals(userId, task.getAssignee())) {
                        return Mono.just(ResultVO.<Void>fail("不在认领名单内: assignee=" + task.getAssignee()));
                    }
                    // CAS 认领自己的行 → 清理同节点其他候选行（两步同事务）
                    return taskRepo.claim(task.getId(), userId)
                            .filter(rows -> rows > 0)
                            .flatMap(_ -> taskRepo.deleteOtherPending(
                                            task.getNodeInstanceId(), task.getId())
                                    .doOnNext(ignored -> log.info(
                                            "[TaskServiceImpl] 认领成功 taskNo={}", req.getTaskNo()))
                                    .thenReturn(ResultVO.<Void>ok()))
                            .switchIfEmpty(Mono.just(ResultVO.fail("认领失败：已被他人认领")));
                })
                .switchIfEmpty(Mono.just(ResultVO.fail(NO_TASK + ": " + req.getTaskNo())));
        return claimed.as(txOperator::transactional);
    }

    @Override
    public Mono<ResultVO<FlowTaskRes>> complete(CompleteTaskReq req) {
        log.info("[TaskServiceImpl] 完成任务 taskNo={}, result={}, userId={}",
                req.getTaskNo(), req.getResult(), req.getUserId());
        return taskRepo.findByTaskNo(req.getTaskNo())
                .flatMap(task -> {
                    if (!CLAIMED.equals(task.getStatus())) {
                        return Mono.just(ResultVO.<FlowTaskRes>fail("任务状态不允许完成: " + task.getStatus()));
                    }
                    if (!Objects.equals(req.getUserId(), task.getAssignee())) {
                        return Mono.just(ResultVO.<FlowTaskRes>fail("只有认领人才能完成任务"));
                    }
                    // 1) 事务内：CAS 标记 COMPLETED → 归档已办表 → 物理删除待办行
                    Mono<FlowTask> done = taskRepo.completeTask(task.getId(), req.getUserId())
                            .filter(rows -> rows > 0)
                            .flatMap(_ -> taskDoneRepo.archiveById(
                                            task.getId(), COMPLETED,
                                            req.getResult(), req.getComment(), req.getUserId())
                                    .then(taskRepo.deleteByIdAndStatus(task.getId(), COMPLETED))
                                    .thenReturn(task));
                    // 2) 事务提交后：发布事件，由引擎推进流程
                    return done.as(txOperator::transactional)
                            .map(t -> {
                                var meta = FlowEventMetadata.of(
                                        t.getInstanceId(), "", "TASK_COMPLETED");
                                eventBus.publish(TaskCompletedEvent.of(
                                        meta, t.getId(), t.getTaskNo(), t.getNodeInstanceId(),
                                        req.getResult(), req.getComment(), req.getUserId()));
                                return ResultVO.<FlowTaskRes>ok();
                            })
                            .switchIfEmpty(Mono.just(ResultVO.fail("任务完成失败")));
                })
                .switchIfEmpty(Mono.just(ResultVO.fail(NO_TASK + ": " + req.getTaskNo())));
    }

    @Override
    public Mono<ResultVO<Void>> cancel(CancelTaskReq req) {
        log.info("[TaskServiceImpl] 取消任务 taskNo={}", req.getTaskNo());
        Mono<ResultVO<Void>> cancelled = taskRepo.findByTaskNo(req.getTaskNo())
                .flatMap(task -> {
                    if (!PENDING.equals(task.getStatus()) && !CLAIMED.equals(task.getStatus())) {
                        return Mono.just(ResultVO.<Void>fail("任务状态不允许取消: " + task.getStatus()));
                    }
                    return taskRepo.cancelTask(task.getId(), req.getOperator())
                            .filter(rows -> rows > 0)
                            .flatMap(rows -> taskDoneRepo.archiveById(
                                            task.getId(), CANCELLED, "", "", req.getOperator())
                                    .then(taskRepo.deleteByIdAndStatus(task.getId(), CANCELLED))
                                    .doOnNext(ignored -> log.info(
                                            "[TaskServiceImpl] 任务已取消 taskNo={}", req.getTaskNo()))
                                    .thenReturn(ResultVO.<Void>ok()))
                            .switchIfEmpty(Mono.just(ResultVO.fail("取消失败")));
                })
                .switchIfEmpty(Mono.just(ResultVO.fail(NO_TASK + ": " + req.getTaskNo())));
        return cancelled.as(txOperator::transactional);
    }

    @Override
    public Mono<ResultVO<Void>> transfer(TransferTaskReq req) {
        log.info("[TaskServiceImpl] 转交任务 taskNo={}, targetUser={}", req.getTaskNo(), req.getTargetUser());
        Mono<ResultVO<Void>> transferred = taskRepo.findByTaskNo(req.getTaskNo())
                .flatMap(task -> {
                    if (!CLAIMED.equals(task.getStatus())) {
                        return Mono.just(ResultVO.<Void>fail("只能转交已认领的任务"));
                    }
                    if (!Objects.equals(req.getOperator(), task.getAssignee())) {
                        return Mono.just(ResultVO.<Void>fail("只有认领人才能转交任务"));
                    }
                    // 旧任务：CAS 置 TRANSFERRED → 归档已办表 → 物理删除
                    // 新任务：目标人 PENDING 待办行，prev_task_id 指向已办表原任务
                    return taskRepo.transferOut(task.getId(), req.getOperator())
                            .filter(rows -> rows > 0)
                            .flatMap(_ -> taskDoneRepo.archiveById(
                                            task.getId(), TRANSFERRED, "", "",
                                            req.getOperator())
                                    .then(taskRepo.deleteByIdAndStatus(task.getId(), TRANSFERRED))
                                    .then(Mono.defer(() -> {
                                        var newTask = FlowTask.builder()
                                                .instanceId(task.getInstanceId())
                                                .nodeInstanceId(task.getNodeInstanceId())
                                                .taskNo("T-" + UUID.randomUUID().toString()
                                                        .replace("-", "").substring(0, 16))
                                                .taskName(task.getTaskName())
                                                .status(PENDING)
                                                .assignee(req.getTargetUser())
                                                .priority(task.getPriority())
                                                .prevTaskId(task.getId())
                                                .createUser(req.getOperator())
                                                .updateUser(req.getOperator())
                                                .build();
                                        return taskRepo.save(newTask);
                                    }))
                                    .thenReturn(ResultVO.<Void>ok()))
                            .switchIfEmpty(Mono.just(ResultVO.fail("转交失败")));
                })
                .switchIfEmpty(Mono.just(ResultVO.fail(NO_TASK + ": " + req.getTaskNo())));
        return transferred.as(txOperator::transactional);
    }

    // ==================== 辅助 ====================

    /**
     * Entity → FlowTaskRes 内联转换。
     * proto 字段保持不变以兼容前端；终态数据（result/comment/completedBy）
     * 在待办视角下恒为空，完整信息见已办表。
     */
    private FlowTaskRes toTaskRes(FlowTask i) {
        return FlowTaskRes.newBuilder()
                .setId(i.getId())
                .setInstanceId(i.getInstanceId())
                .setNodeInstanceId(i.getNodeInstanceId())
                .setTaskNo(i.getTaskNo())
                .setTaskName(i.getTaskName())
                .setStatus(i.getStatus())
                .setAssignee(i.getAssignee())
                .setCandidateUsers("[]")
                .setClaimedBy(CLAIMED.equals(i.getStatus()) ? i.getAssignee() : "")
                .setClaimedTime(formatTime(i.getClaimedTime()))
                .setCompletedBy("")
                .setCompletedTime("")
                .setResult("")
                .setComment("")
                .setDueTime(formatTime(i.getDueTime()))
                .setPriority(i.getPriority())
                .build();
    }

    private String formatTime(java.time.LocalDateTime time) {
        return Optional.ofNullable(time)
                .map(t -> DateTimeFormatter.ofPattern(YYYY_MM_DD_HH_MM_SS).format(t))
                .orElse("");
    }
}