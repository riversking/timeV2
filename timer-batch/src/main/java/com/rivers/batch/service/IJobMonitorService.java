package com.rivers.batch.service;

import com.rivers.core.vo.ResultVO;
import com.rivers.proto.*;
import reactor.core.publisher.Mono;

public interface IJobMonitorService {

    Mono<ResultVO<JobExecutionRes>> getJobExecutionCounts();

    Mono<ResultVO<SchedulesRes>> getSchedules();

    Mono<ResultVO<JobDateExecutionsRes>> getJobExecutionByDate(JobDateExecutionReq jobExecutionReq);
}
