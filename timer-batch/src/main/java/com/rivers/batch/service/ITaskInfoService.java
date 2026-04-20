package com.rivers.batch.service;

import com.rivers.core.vo.ResultVO;
import com.rivers.proto.*;

public interface ITaskInfoService {

    ResultVO<Void> saveTaskInfo(SaveTaskInfoReq saveTaskInfoReq);

    ResultVO<Void> updateTaskInfo(UpdateTaskInfoReq updateTaskInfoReq);

    ResultVO<Void> pauseTask(CommonTaskReq commonTaskReq);

    ResultVO<Void> resumeTask(CommonTaskReq commonTaskReq);

    ResultVO<Void> deleteTask(CommonTaskReq commonTaskReq);

    ResultVO<Void> runTask(CommonTaskReq commonTaskReq);

    ResultVO<JobPageRes> getJobPage(JobPageReq jobPageReq);

    ResultVO<JobDetailRes> getJobDetail(CommonTaskReq commonTaskReq);
}
