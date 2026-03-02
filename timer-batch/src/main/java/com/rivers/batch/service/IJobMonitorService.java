package com.rivers.batch.service;

import com.rivers.core.vo.ResultVO;
import com.rivers.proto.JobExecutionRes;
import com.rivers.proto.SchedulesRes;
import reactor.core.publisher.Mono;

public interface IJobMonitorService {

    Mono<ResultVO<JobExecutionRes>> getJobExecutionCounts();

    Mono<ResultVO<SchedulesRes>> getSchedules();
}
