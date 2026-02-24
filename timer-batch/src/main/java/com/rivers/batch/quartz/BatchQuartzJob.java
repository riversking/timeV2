package com.rivers.batch.quartz;

import com.rivers.batch.factory.BatchFactory;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.scheduling.quartz.QuartzJobBean;

@Slf4j
public class BatchQuartzJob extends QuartzJobBean {

    private final JobOperator jobOperator;

    private final BatchFactory batchFactory;

    public BatchQuartzJob(JobOperator jobOperator, BatchFactory batchFactory) {
        this.jobOperator = jobOperator;
        this.batchFactory = batchFactory;
    }

    @Override
    @NullMarked
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        try {
            String jobName = context.getJobDetail().getKey().getName();
            JobDataMap jobDataMap = context.getJobDetail().getJobDataMap();
            log.info("Starting batch job execution: {}", jobName);
            // 获取动态创建的Job
            Job dynamicJob = batchFactory.getDynamicJob(jobName);
            if (dynamicJob == null) {
                log.warn("Dynamic job not found: {}, falling back to default businessJob", dynamicJob);
                // 如果找不到动态Job，使用默认的businessJob
                dynamicJob = batchFactory.getOrCreateDynamicJob(jobName);
            }
            JobParameters jobParameters = new JobParametersBuilder()
                    .addLong("timestamp", System.currentTimeMillis())
                    .addString("jobName", jobName)
                    .addString("taskName", jobDataMap.getString("taskName"))
                    .addString("serviceName", jobDataMap.getString("serviceName"))
                    .addString("trigger", "quartz")
                    .toJobParameters();
            JobExecution jobExecution = jobOperator.start(dynamicJob, jobParameters);
            log.info("Batch job {} completed with status: {}", jobName, jobExecution.getStatus());
        } catch (Exception e) {
            log.error("Error executing batch job", e);
            throw new JobExecutionException(e);
        }
    }
}
