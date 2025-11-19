package com.rivers.batch.quartz;

import lombok.extern.slf4j.Slf4j;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.quartz.QuartzJobBean;

@Slf4j
public class BatchQuartzJob extends QuartzJobBean {

    private final JobLauncher jobLauncher;

    @Qualifier("businessJob")
    private final Job businessJob;

    public BatchQuartzJob(JobLauncher jobLauncher, Job businessJob) {
        this.jobLauncher = jobLauncher;
        this.businessJob = businessJob;
    }

    @Override
    protected void executeInternal(@NonNull JobExecutionContext context) throws JobExecutionException {
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
            JobExecution jobExecution = jobLauncher.run(businessJob, jobParameters);
            log.info("Batch job {} completed with status: {}", jobName, jobExecution.getStatus());
        } catch (Exception e) {
            log.error("Error executing batch job", e);
            throw new JobExecutionException(e);
        }
    }
}
