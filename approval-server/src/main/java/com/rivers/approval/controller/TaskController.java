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

    @PostMapping("complete")
    public Mono<ResultVO<FlowTaskRes>> complete(@RequestBody CompleteTaskReq completeTaskReq) {
        return taskService.complete(completeTaskReq);
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