package com.rivers.batch.service;

import com.rivers.core.vo.ResultVO;
import com.rivers.proto.JobExecutionRes;
import reactor.core.publisher.Mono;

public interface IJobMonitorService {

    Mono<ResultVO<JobExecutionRes>> getJobExecutionCounts();
}
