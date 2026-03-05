package com.rivers.batch.service;

import com.rivers.core.vo.ResultVO;
import com.rivers.proto.JobDateExecutionReq;
import com.rivers.proto.JobDateExecutionsRes;
import com.rivers.proto.JobExecutionRes;
import com.rivers.proto.SchedulesRes;

public interface IJobMonitorService {

    ResultVO<JobExecutionRes> getJobExecutionCounts();

    ResultVO<SchedulesRes> getSchedules();

    ResultVO<JobDateExecutionsRes> getJobExecutionByDate(JobDateExecutionReq jobExecutionReq);
}
