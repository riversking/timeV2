package com.rivers.batch.controller;

import com.rivers.batch.service.ITaskInfoService;
import com.rivers.core.vo.ResultVO;
import com.rivers.proto.CommonTaskReq;
import com.rivers.proto.SaveTaskInfoReq;
import com.rivers.proto.UpdateTaskInfoReq;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("task")
public class TaskController {

    private final ITaskInfoService taskInfoService;

    public TaskController(ITaskInfoService taskInfoService) {
        this.taskInfoService = taskInfoService;
    }

    @PostMapping("saveTaskInfo")
    public ResultVO<Void> saveTaskInfo(@RequestBody SaveTaskInfoReq saveTaskInfoReq) {
        return taskInfoService.saveTaskInfo(saveTaskInfoReq);
    }

    @PostMapping("updateTaskInfo")
    public ResultVO<Void> updateTaskInfo(@RequestBody UpdateTaskInfoReq updateTaskInfoReq) {
        return taskInfoService.updateTaskInfo(updateTaskInfoReq);
    }

    @PostMapping("pauseTask")
    public ResultVO<Void> pauseTask(@RequestBody CommonTaskReq commonTaskReq) {
        return taskInfoService.pauseTask(commonTaskReq);
    }

    @PostMapping("resumeTask")
    public ResultVO<Void> resumeTask(@RequestBody CommonTaskReq commonTaskReq) {
        return taskInfoService.resumeTask(commonTaskReq);
    }

    @PostMapping("deleteTask")
    public ResultVO<Void> deleteTask(@RequestBody CommonTaskReq commonTaskReq) {
        return taskInfoService.deleteTask(commonTaskReq);
    }

    @PostMapping("runTask")
    public ResultVO<Void> runTask(@RequestBody CommonTaskReq commonTaskReq) {
        return taskInfoService.runTask(commonTaskReq);
    }

}
