package com.rivers.batch.controller;

import com.rivers.batch.service.IJobMonitorService;
import com.rivers.core.vo.ResultVO;
import com.rivers.proto.JobExecutionRes;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("jobMonitor")
public class JobMonitorController {

    private final IJobMonitorService jobMonitorService;

    public JobMonitorController(IJobMonitorService jobMonitorService) {
        this.jobMonitorService = jobMonitorService;
    }

    @PostMapping("getJobExecutionCounts")
    public Mono<ResultVO<JobExecutionRes>> getJobExecutionCounts() {
        return jobMonitorService.getJobExecutionCounts();
    }
}
