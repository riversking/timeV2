package com.rivers.approval.controller;

import com.rivers.approval.service.ITaskService;
import com.rivers.core.vo.ResultVO;
import com.rivers.proto.*;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * 任务控制器 — 任务池全生命周期操作。
 * <p>
 * 办理动作拆分为三个语义化接口，result 由服务端固定：
 * <ul>
 *   <li>approve — 审批通过（APPROVED），继续推进后续节点</li>
 *   <li>reject  — 拒绝（REJECTED），实例直接 COMPLETED</li>
 *   <li>return  — 退回（RETURNED），实例直接 COMPLETED</li>
 * </ul>
 */
@RestController
@RequestMapping("task")
public class TaskController {

    private final ITaskService taskService;

    public TaskController(ITaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping("listTodo")
    public Mono<ResultVO<TaskListRes>> listTodo(@RequestBody ListTaskReq listTaskReq) {
        return taskService.listTodo(listTaskReq);
    }

    @PostMapping("listClaimable")
    public Mono<ResultVO<TaskListRes>> listClaimable(@RequestBody ListTaskReq listTaskReq) {
        return taskService.listClaimable(listTaskReq);
    }

    @PostMapping("getByTaskNo")
    public Mono<ResultVO<FlowTaskRes>> getByTaskNo(@RequestBody TaskNoReq taskNoReq) {
        return taskService.getByTaskNo(taskNoReq);
    }

    @PostMapping("claim")
    public Mono<ResultVO<Void>> claim(@RequestBody ClaimTaskReq claimTaskReq) {
        return taskService.claim(claimTaskReq);
    }

    @PostMapping("approve")
    public Mono<ResultVO<FlowTaskRes>> approve(@RequestBody TaskActionReq taskActionReq) {
        return taskService.approve(taskActionReq);
    }

    @PostMapping("reject")
    public Mono<ResultVO<FlowTaskRes>> reject(@RequestBody TaskActionReq taskActionReq) {
        return taskService.reject(taskActionReq);
    }

    @PostMapping("return")
    public Mono<ResultVO<FlowTaskRes>> returnTask(@RequestBody TaskActionReq taskActionReq) {
        return taskService.returnTask(taskActionReq);
    }

    @PostMapping("cancel")
    public Mono<ResultVO<Void>> cancel(@RequestBody CancelTaskReq cancelTaskReq) {
        return taskService.cancel(cancelTaskReq);
    }

    @PostMapping("transfer")
    public Mono<ResultVO<Void>> transfer(@RequestBody TransferTaskReq transferTaskReq) {
        return taskService.transfer(transferTaskReq);
    }
}