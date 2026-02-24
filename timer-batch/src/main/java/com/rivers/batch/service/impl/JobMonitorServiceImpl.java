package com.rivers.batch.service.impl;

import com.rivers.batch.service.IJobMonitorService;
import com.rivers.core.vo.ResultVO;
import com.rivers.proto.JobExecutionRes;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
@Slf4j
public class JobMonitorServiceImpl implements IJobMonitorService {

    private final JobRepository jobRepository;

    public JobMonitorServiceImpl(JobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    @Override
    public Mono<ResultVO<JobExecutionRes>> getJobExecutionCounts() {
        List<String> jobNames = jobRepository.getJobNames();
        jobNames.forEach(i -> {
            List<JobInstance> jobInstances = jobRepository.getJobInstances(i, 0, Integer.MAX_VALUE);
            log.info("Job name: {}", i);
            log.info("Job instances: {}", jobInstances);
        });
        return null;
    }
}
