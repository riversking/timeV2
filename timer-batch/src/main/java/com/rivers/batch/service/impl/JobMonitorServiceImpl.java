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


    /**package com.rivers.batch.service.impl;

     import com.rivers.batch.service.IJobMonitorService;
     import com.rivers.core.vo.ResultVO;
     import com.rivers.proto.JobExecutionRes;
     import lombok.extern.slf4j.Slf4j;
     import org.springframework.batch.core.*;
     import org.springframework.batch.core.repository.JobRepository;
     import org.springframework.stereotype.Service;
     import reactor.core.publisher.Mono;

     import java.util.HashMap;
     import java.util.List;
     import java.util.Map;

     @Service
     @Slf4j
     public class JobMonitorServiceImpl implements IJobMonitorService {

     private final JobRepository jobRepository;

     public JobMonitorServiceImpl(JobRepository jobRepository) {
     this.jobRepository = jobRepository;
     }

     @Override
     public Mono<ResultVO<JobExecutionRes>> getJobExecutionCounts() {
     try {
     JobExecutionRes response = new JobExecutionRes();
     Map<String, Integer> statusCounts = new HashMap<>();

     // 初始化状态计数器
     statusCounts.put("COMPLETED", 0);
     statusCounts.put("FAILED", 0);
     statusCounts.put("STOPPED", 0);
     statusCounts.put("STARTED", 0);
     statusCounts.put("STARTING", 0);
     statusCounts.put("STOPPING", 0);
     statusCounts.put("ABANDONED", 0);
     statusCounts.put("UNKNOWN", 0);

     // 获取所有Job名称
     List<String> jobNames = jobRepository.getJobNames();
     log.info("Found {} job names in repository", jobNames.size());

     int totalExecutions = 0;

     // 遍历每个Job名称
     for (String jobName : jobNames) {
     // 获取该Job的所有实例
     List<JobInstance> jobInstances = jobRepository.getJobInstances(jobName, 0, Integer.MAX_VALUE);
     log.debug("Job '{}' has {} instances", jobName, jobInstances.size());

     // 遍历每个实例的所有执行记录
     for (JobInstance jobInstance : jobInstances) {
     List<JobExecution> executions = jobRepository.getJobExecutions(jobInstance);
     log.debug("Job instance {} has {} executions", jobInstance.getInstanceId(), executions.size());

     for (JobExecution execution : executions) {
     BatchStatus status = execution.getStatus();
     String statusStr = status.toString();

     // 更新状态计数
     statusCounts.merge(statusStr, 1, Integer::sum);
     totalExecutions++;

     log.debug("Execution ID: {}, Status: {}, Start Time: {}, End Time: {}",
     execution.getId(),
     statusStr,
     execution.getStartTime(),
     execution.getEndTime());
     }
     }
     }

     // 设置响应数据
     response.setTotalExecutions(totalExecutions);
     response.setCompletedCount(statusCounts.get("COMPLETED"));
     response.setFailedCount(statusCounts.get("FAILED"));
     response.setStoppedCount(statusCounts.get("STOPPED"));
     response.setStartedCount(statusCounts.get("STARTED"));
     response.setStartingCount(statusCounts.get("STARTING"));
     response.setStoppingCount(statusCounts.get("STOPPING"));
     response.setAbandonedCount(statusCounts.get("ABANDONED"));
     response.setUnknownCount(statusCounts.get("UNKNOWN"));

     // 计算成功率
     if (totalExecutions > 0) {
     double successRate = (double) statusCounts.get("COMPLETED") / totalExecutions * 100;
     response.setSuccessRate(String.format("%.2f%%", successRate));
     } else {
     response.setSuccessRate("0.00%");
     }

     log.info("Job execution statistics - Total: {}, Completed: {}, Failed: {}, Success Rate: {}",
     totalExecutions,
     statusCounts.get("COMPLETED"),
     statusCounts.get("FAILED"),
     response.getSuccessRate());

     return Mono.just(ResultVO.ok(response));

     } catch (Exception e) {
     log.error("Error getting job execution counts", e);
     return Mono.just(ResultVO.fail("获取任务执行统计失败: " + e.getMessage()));
     }
     }
     }
     */
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
