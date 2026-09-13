package com.rivers.approval.service.impl;

import com.rivers.approval.entity.FlowTask;
import com.rivers.approval.entity.FlowTrack;
import com.rivers.approval.event.FlowEventBus;
import com.rivers.approval.event.FlowEventMetadata;
import com.rivers.approval.event.TaskCompletedEvent;
import com.rivers.approval.repository.FlowTaskDoneRepository;
import com.rivers.approval.repository.FlowTaskRepository;
import com.rivers.approval.repository.FlowTrackRepository;
import com.rivers.approval.service.ITaskService;
import com.rivers.core.vo.ResultVO;
import com.rivers.proto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 任务服务实现。
 * <p>
 * 新模型约定：
 * <ul>
 *   <li>待办表（flow_task）只存在 PENDING / CLAIMED / WAITING 三种活跃状态，任务终态物理删除</li>
 *   <li>终态快照（COMPLETED / TRANSFERRED / CANCELLED）统一归档到已办表（flow_task_done）</li>
 *   <li>所有写操作遵循"CAS 标记 → 归档 → 删除"三步，同一事务内完成</li>
 *   <li>业务失败不抛异常：统一返回 ResultVO.fail(msg)</li>
 *   <li>任务模式 taskMode：
 *       CLAIM 需领单 / ANY_ONE 任一人 / ALL 全部 / SEQUENTIAL 串签；
 *       推进判定统一在事务提交后当前读统计剩余活跃行，0 才发推进事件</li>
 *   <li>办理动作拆分为三个接口：approve（审批）/ reject（拒绝）/ return（退回），
 *       result 由服务端固定，前端不再传</li>
 *   <li>转交：目标人新任务直接 CLAIMED（已接单），无需再次认领</li>
 *   <li>跟踪链：领单/转交在事务内直接写 flow_track（入库即展示）</li>
 * </ul>
 */
@Service
@Slf4j
public class TaskServiceImpl implements ITaskService {

    private static final String CLAIM = "CLAIM";
    private static final String ANY_ONE = "ANY_ONE";
    private static final String ALL = "ALL";
    private static final String SEQUENTIAL = "SEQUENTIAL";
    private static final String PENDING = "PENDING";
    private static final String CLAIMED = "CLAIMED";
    private static final String COMPLETED = "COMPLETED";
    private static final String CANCELLED = "CANCELLED";
    private static final String TRANSFERRED = "TRANSFERRED";
    private static final String NO_TASK = "任务不存在";
    private static final String YYYY_MM_DD_HH_MM_SS = "yyyy-MM-dd HH:mm:ss";

    private final FlowTaskRepository taskRepo;
    private final FlowTaskDoneRepository taskDoneRepo;
    private final FlowTrackRepository trackRepo;
    private final FlowEventBus eventBus;
    private final TransactionalOperator txOperator;

    public TaskServiceImpl(FlowTaskRepository taskRepo, FlowTaskDoneRepository taskDoneRepo,
                           FlowTrackRepository trackRepo,
                           FlowEventBus eventBus, TransactionalOperator txOperator) {
        this.taskRepo = taskRepo;
        this.taskDoneRepo = taskDoneRepo;
        this.trackRepo = trackRepo;
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
                    var mode = modeOf(task);
                    if (!CLAIM.equals(mode)) {
                        return Mono.just(ResultVO.<Void>fail("该任务无需认领，请直接办理"));
                    }
                    if (!PENDING.equals(task.getStatus())) {
                        return Mono.just(ResultVO.<Void>fail("任务状态不允许认领: " + task.getStatus()));
                    }
                    if (!Objects.equals(userId, task.getAssignee())) {
                        return Mono.just(ResultVO.<Void>fail("不在认领名单内: assignee=" + task.getAssignee()));
                    }
                    // CAS 认领自己的行 → 写跟踪行 → 清理同节点其他候选行（同事务）
                    return taskRepo.claim(task.getId(), userId)
                            .filter(rows -> rows > 0)
                            .flatMap(_ -> trackRepo.save(FlowTrack.builder()
                                            .instanceId(task.getInstanceId())
                                            .nodeInstanceId(task.getNodeInstanceId())
                                            .trackType(CLAIMED)
                                            .nodeName(task.getTaskName())
                                            .assignee(userId)
                                            .nextAssignee(userId)
                                            .taskTime(task.getCreateTime())
                                            .actionTime(LocalDateTime.now(ZoneId.systemDefault()))
                                            .createUser(userId)
                                            .updateUser(userId)
                                            .build())
                                    .then(taskRepo.deleteOtherPending(
                                            task.getNodeInstanceId(), task.getId()))
                                    .doOnNext(ignored -> log.info(
                                            "[TaskServiceImpl] 认领成功 taskNo={}", req.getTaskNo()))
                                    .thenReturn(ResultVO.<Void>ok()))
                            .switchIfEmpty(Mono.just(ResultVO.fail("认领失败：已被他人认领")));
                })
                .switchIfEmpty(Mono.just(ResultVO.fail(NO_TASK + ": " + req.getTaskNo())));
        return claimed.as(txOperator::transactional);
    }

    @Override
    public Mono<ResultVO<FlowTaskRes>> approve(TaskActionReq req) {
        return doComplete(req, "APPROVED");
    }

    @Override
    public Mono<ResultVO<FlowTaskRes>> reject(TaskActionReq req) {
        return doComplete(req, "REJECTED");
    }

    @Override
    public Mono<ResultVO<FlowTaskRes>> returnTask(TaskActionReq req) {
        return doComplete(req, "RETURNED");
    }

    /**
     * 三个办理接口的公共链路：加载任务 → 校验 → 事务内落库 → 提交后统计推进。
     * result 由入口固定，杜绝前端传错值。
     */
    private Mono<ResultVO<FlowTaskRes>> doComplete(TaskActionReq req, String result) {
        log.info("[TaskServiceImpl] 办理任务 taskNo={}, result={}, userId={}",
                req.getTaskNo(), result, req.getUserId());
        return taskRepo.findByTaskNo(req.getTaskNo())
                .flatMap(task -> completeTask(task, result, req.getComment(), req.getUserId()))
                .switchIfEmpty(Mono.just(ResultVO.fail(NO_TASK + ": " + req.getTaskNo())));
    }

    /**
     * 按任务模式完成：
     * <ul>
     *   <li>CLAIM      — 已认领行完成（原语义）</li>
     *   <li>ANY_ONE    — 任一人直接完成，同节点其他候选行作废</li>
     *   <li>ALL        — 各自完成，全部完成后节点才推进</li>
     *   <li>SEQUENTIAL — 按序完成，当前行完成后激活下一顺位</li>
     * </ul>
     * 推进判定统一放在事务提交后的当前读统计：剩余活跃行数为 0 才发推进事件，
     * 避免并发完成时（REPEATABLE READ 快照）漏推节点。
     */
    private Mono<ResultVO<FlowTaskRes>> completeTask(FlowTask task, String result,
                                                     String comment, String userId) {
        var mode = modeOf(task);
        switch (mode) {
            case CLAIM -> {
                if (!CLAIMED.equals(task.getStatus())) {
                    return Mono.just(ResultVO.fail("任务状态不允许完成: " + task.getStatus()));
                }
                if (!Objects.equals(userId, task.getAssignee())) {
                    return Mono.just(ResultVO.fail("只有认领人才能完成任务"));
                }
            }
            case ANY_ONE, ALL, SEQUENTIAL -> {
                if (!PENDING.equals(task.getStatus())) {
                    return Mono.just(ResultVO.fail("任务状态不允许完成: " + task.getStatus()));
                }
                if (!Objects.equals(userId, task.getAssignee())) {
                    return Mono.just(ResultVO.fail("只有任务办理人才能完成"));
                }
            }
            default -> {
                return Mono.just(ResultVO.fail("不支持的任务模式: " + mode));
            }
        }
        // 1) 事务内：CAS 标记 COMPLETED → 归档已办表 → 物理删除 → 模式收尾
        var cas = CLAIM.equals(mode)
                ? taskRepo.completeTask(task.getId(), userId)
                : taskRepo.completeNoClaim(task.getId(), userId);
        Mono<Void> done = cas.filter(rows -> rows > 0)
                .flatMap(_ -> taskDoneRepo.archiveById(task.getId(), COMPLETED,
                                result, comment, userId)
                        .then(taskRepo.deleteByIdAndStatus(task.getId(), COMPLETED))
                        .then(modeCleanup(task, mode)));
        // 2) 事务提交后：当前读统计剩余活跃行，0 才推进节点；事件恒发布（审计每次办理）
        return done.as(txOperator::transactional)
                .then(taskRepo.countActiveByNodeInstanceId(task.getNodeInstanceId()))
                .map(count -> {
                    boolean advance = count == 0;
                    publishCompleted(task, result, comment, userId, advance);
                    return ResultVO.<FlowTaskRes>ok();
                })
                .switchIfEmpty(Mono.just(ResultVO.fail("任务完成失败")));
    }

    /**
     * 模式专属收尾（事务内执行）：
     * ANY_ONE    — 作废同节点其他候选行
     * SEQUENTIAL — 激活下一顺位（WAITING → PENDING）
     * 其余模式无收尾动作。
     */
    private Mono<Void> modeCleanup(FlowTask task, String mode) {
        return switch (mode) {
            case ANY_ONE -> taskRepo.deleteOtherPending(task.getNodeInstanceId(), task.getId())
                    .then();
            case SEQUENTIAL -> taskRepo.activateNextWaiting(task.getNodeInstanceId(),
                            task.getSeq() == null ? 1 : task.getSeq() + 1)
                    .then();
            default -> Mono.empty();
        };
    }

    private void publishCompleted(FlowTask task, String result, String comment,
                                  String userId, boolean advance) {
        var meta = FlowEventMetadata.of(task.getInstanceId(), "", "TASK_COMPLETED")
                .withOperator(userId, userId);
        eventBus.publish(TaskCompletedEvent.of(

                meta, task.getId(), task.getTaskNo(), task.getNodeInstanceId(),
                task.getTaskName(), result, comment, userId, advance));
        if (!advance) {
            log.info("[TaskServiceImpl] 节点办理未集齐，暂不推进 taskNo={}", task.getTaskNo());
        }
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
                            .flatMap(_ -> taskDoneRepo.archiveById(
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
                    // 新任务：目标人直接 CLAIMED（已接单），无需再次认领，出现在其待办列表
                    // 跟踪行：转交行为写 flow_track（next=目标人）
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
                                                .status(CLAIMED)
                                                .assignee(req.getTargetUser())
                                                .priority(task.getPriority())
                                                .taskMode(task.getTaskMode())
                                                .seq(task.getSeq())
                                                .prevTaskId(task.getId())
                                                .createUser(req.getOperator())
                                                .updateUser(req.getOperator())
                                                .build();
                                        return taskRepo.save(newTask)
                                                .then(trackRepo.save(FlowTrack.builder()
                                                        .instanceId(task.getInstanceId())
                                                        .nodeInstanceId(task.getNodeInstanceId())
                                                        .trackType(TRANSFERRED)
                                                        .nodeName(task.getTaskName())
                                                        .assignee(req.getOperator())
                                                        .nextAssignee(req.getTargetUser())
                                                        .taskTime(task.getCreateTime())
                                                        .actionTime(LocalDateTime.now(ZoneId.systemDefault()))
                                                        .createUser(req.getOperator())
                                                        .updateUser(req.getOperator())
                                                        .build()));
                                    }))
                                    .thenReturn(ResultVO.<Void>ok()))
                            .switchIfEmpty(Mono.just(ResultVO.fail("转交失败")));
                })
                .switchIfEmpty(Mono.just(ResultVO.fail(NO_TASK + ": " + req.getTaskNo())));
        return transferred.as(txOperator::transactional);
    }

    // ==================== 辅助 ====================

    /**
     * 任务模式（存量行无该字段时按 CLAIM 处理）
     */
    private String modeOf(FlowTask task) {
        return Optional.ofNullable(task.getTaskMode())
                .filter(m -> !m.isBlank())
                .orElse(CLAIM);
    }

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