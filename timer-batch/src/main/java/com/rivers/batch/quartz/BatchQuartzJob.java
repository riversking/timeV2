package com.rivers.batch.quartz;

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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.quartz.QuartzJobBean;

@Slf4j
public class BatchQuartzJob extends QuartzJobBean {

    private final JobOperator jobOperator;

    @Qualifier("businessJob")
    private final Job businessJob;

    public BatchQuartzJob(JobOperator jobOperator, Job businessJob) {
        this.jobOperator = jobOperator;
        this.businessJob = businessJob;
    }

    @Override
    @NullMarked
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        try {
            String jobName = context.getJobDetail().getKey().getName();
            JobDataMap jobDataMap = context.getJobDetail().getJobDataMap();
            log.info("Starting batch job execution: {}", jobName);
            JobParameters jobParameters = new JobParametersBuilder()
                    .addLong("timestamp", System.currentTimeMillis())
                    .addString("jobName", jobName)
                    .addString("taskName", jobDataMap.getString("taskName"))
                    .addString("serviceName", jobDataMap.getString("serviceName"))
                    .addString("trigger", "quartz")
                    .toJobParameters();
            JobExecution jobExecution = jobOperator.start(businessJob, jobParameters);
            log.info("Batch job {} completed with status: {}", jobName, jobExecution.getStatus());
        } catch (Exception e) {
            log.error("Error executing batch job", e);
            throw new JobExecutionException(e);
        }
    }
}
