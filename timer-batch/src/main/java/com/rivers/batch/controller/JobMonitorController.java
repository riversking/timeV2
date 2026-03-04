package com.rivers.batch.controller;

import com.rivers.batch.service.IJobMonitorService;
import com.rivers.core.vo.ResultVO;
import com.rivers.proto.JobExecutionReq;
import com.rivers.proto.JobExecutionRes;
import com.rivers.proto.SchedulesRes;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    @PostMapping("getSchedules")
    public Mono<ResultVO<SchedulesRes>> getSchedules() {
        return jobMonitorService.getSchedules();
    }

    @PostMapping("getJobExecutionByDate")
    public Mono<ResultVO<JobExecutionRes>> getJobExecutionByDate(@RequestBody JobExecutionReq jobExecutionReq) {
        return jobMonitorService.getJobExecutionByDate(jobExecutionReq);
    }
}
