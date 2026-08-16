package com.rivers.approval.service.impl;

import com.rivers.approval.entity.FlowTask;
import com.rivers.approval.event.FlowEventBus;
import com.rivers.approval.event.FlowEventMetadata;
import com.rivers.approval.event.TaskCompletedEvent;
import com.rivers.approval.repository.FlowTaskRepository;
import com.rivers.approval.service.ITaskService;
import com.rivers.core.vo.ResultVO;
import com.rivers.proto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;

/**
 * 任务服务实现。
 */
@Service
@Slf4j
public class TaskServiceImpl implements ITaskService {

    private static final String CLAIMED = "CLAIMED";
    private static final String PENDING = "PENDING";
    private static final String NO_TASK = "任务不存在";
    private static final String YYYY_MM_DD_HH_MM_SS = "yyyy-MM-dd HH:mm:ss";
    private final FlowTaskRepository taskRepo;
    private final FlowEventBus eventBus;

    public TaskServiceImpl(FlowTaskRepository taskRepo, FlowEventBus eventBus) {
        this.taskRepo = taskRepo;
        this.eventBus = eventBus;
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
                .defaultIfEmpty(ResultVO.fail(404, NO_TASK + ": " + req.getTaskNo()));
    }

    // ==================== 操作 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Mono<ResultVO<Void>> claim(ClaimTaskReq req) {
        log.info("[TaskServiceImpl] 认领任务 taskNo={}, userId={}", req.getTaskNo(), req.getUserId());
        return taskRepo.findByTaskNo(req.getTaskNo())
                .switchIfEmpty(Mono.error(
                        new IllegalArgumentException(NO_TASK + ": " + req.getTaskNo())))
                .flatMap(task -> {
                    if (!PENDING.equals(task.getStatus())) {
                        return Mono.error(
                                new IllegalStateException("任务状态不允许认领: " + task.getStatus()));
                    }
                    return taskRepo.claim(task.getId(), req.getUserId())
                            .filter(rows -> rows > 0)
                            .switchIfEmpty(Mono.error(
                                    new IllegalStateException("认领失败：已被他人认领或权限不足")))
                            .flatMap(rows -> taskRepo.findByTaskNo(req.getTaskNo()));
                })
                .doOnNext(t -> log.info("[TaskServiceImpl] 认领成功 taskNo={}", t.getTaskNo()))
                .thenReturn(ResultVO.ok());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Mono<ResultVO<FlowTaskRes>> complete(CompleteTaskReq req) {
        log.info("[TaskServiceImpl] 完成任务 taskNo={}, result={}, userId={}",
                req.getTaskNo(), req.getResult(), req.getUserId());
        return taskRepo.findByTaskNo(req.getTaskNo())
                .switchIfEmpty(Mono.error(
                        new IllegalArgumentException(NO_TASK + ": " + req.getTaskNo())))
                .flatMap(task -> {
                    if (!CLAIMED.equals(task.getStatus())) {
                        return Mono.error(
                                new IllegalStateException("任务状态不允许完成: " + task.getStatus()));
                    }
                    if (!req.getUserId().equals(task.getClaimedBy())) {
                        return Mono.error(
                                new IllegalStateException("只有认领人才能完成任务"));
                    }
                    return taskRepo.complete(task.getId(), req.getResult(), req.getComment(), req.getUserId())
                            .filter(rows -> rows > 0)
                            .switchIfEmpty(Mono.error(
                                    new IllegalStateException("任务完成失败")))
                            .flatMap(rows -> taskRepo.findByTaskNo(req.getTaskNo()));
                })
                .doOnNext(t -> {
                    var meta = FlowEventMetadata.of(t.getInstanceId(), "", "TASK_COMPLETED");
                    eventBus.publish(TaskCompletedEvent.of(
                            meta, t.getId(), t.getTaskNo(), t.getNodeInstanceId(),
                            req.getResult(), req.getComment(), req.getUserId()));
                })
                .thenReturn(ResultVO.ok());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Mono<ResultVO<Void>> cancel(CancelTaskReq req) {
        log.info("[TaskServiceImpl] 取消任务 taskNo={}", req.getTaskNo());
        return taskRepo.findByTaskNo(req.getTaskNo())
                .switchIfEmpty(Mono.error(
                        new IllegalArgumentException(NO_TASK + ": " + req.getTaskNo())))
                .flatMap(task -> {
                    if (!PENDING.equals(task.getStatus()) && !CLAIMED.equals(task.getStatus())) {
                        return Mono.error(
                                new IllegalStateException("任务状态不允许取消: " + task.getStatus()));
                    }
                    return taskRepo.cancel(task.getId(), req.getOperator())
                            .filter(rows -> rows > 0)
                            .switchIfEmpty(Mono.<Integer>error(
                                    new IllegalStateException("取消失败")))
                            .thenReturn(task);
                })
                .doOnSuccess(t -> log.info("[TaskServiceImpl] 任务已取消 taskNo={}", req.getTaskNo()))
                .thenReturn(ResultVO.ok());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Mono<ResultVO<Void>> transfer(TransferTaskReq req) {
        log.info("[TaskServiceImpl] 转交任务 taskNo={}, targetUser={}", req.getTaskNo(), req.getTargetUser());
        return taskRepo.findByTaskNo(req.getTaskNo())
                .switchIfEmpty(Mono.error(
                        new IllegalArgumentException(NO_TASK + ": " + req.getTaskNo())))
                .flatMap(task -> {
                    if (!CLAIMED.equals(task.getStatus())) {
                        return Mono.error(
                                new IllegalStateException("只能转交已认领的任务"));
                    }
                    if (!req.getOperator().equals(task.getClaimedBy())) {
                        return Mono.error(
                                new IllegalStateException("只有认领人才能转交任务"));
                    }
                    return taskRepo.transferOut(task.getId(), req.getOperator())
                            .filter(rows -> rows > 0)
                            .switchIfEmpty(Mono.error(
                                    new IllegalStateException("转交失败")))
                            .flatMap(rows -> {
                                var newTask = FlowTask.builder()
                                        .instanceId(task.getInstanceId())
                                        .nodeInstanceId(task.getNodeInstanceId())
                                        .taskNo("T-" + UUID.randomUUID().toString()
                                                .replace("-", "").substring(0, 16))
                                        .taskName(task.getTaskName())
                                        .status(PENDING)
                                        .assignee(req.getTargetUser())
                                        .candidateUsers("[\"" + req.getTargetUser() + "\"]")
                                        .priority(task.getPriority())
                                        .prevTaskId(task.getId())
                                        .createUser(req.getOperator())
                                        .updateUser(req.getOperator())
                                        .build();
                                return taskRepo.save(newTask);
                            });
                })
                .thenReturn(ResultVO.ok());
    }

    // ==================== 辅助 ====================

    /**
     * Entity → FlowTaskRes 内联转换（原文件中的重复代码提取为私有方法）
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
                .setCandidateUsers(i.getCandidateUsers())
                .setClaimedBy(i.getClaimedBy())
                .setClaimedTime(formatTime(i.getClaimedTime()))
                .setCompletedBy(i.getCompletedBy())
                .setCompletedTime(formatTime(i.getCompletedTime()))
                .setResult(i.getResult())
                .setComment(i.getComment())
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