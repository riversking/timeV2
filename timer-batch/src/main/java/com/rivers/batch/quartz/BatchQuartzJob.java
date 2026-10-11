package com.rivers.batch.quartz;

import com.rivers.batch.factory.BatchFactory;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.scheduling.quartz.QuartzJobBean;

/**
 * Quartz 与 Spring Batch 的桥接 Job。
 *
 * @DisallowConcurrentExecution：禁止同一任务（同一 JobDetail）并发执行。
 * 背景：机器休眠/长时间暂停（如 macOS DarkWake）或时钟跳变时，Quartz 的 misfire
 * 恢复可能对同一 trigger 产生「双重释放」，实测出现过同一任务在几十毫秒内被两个
 * worker 并发启动的重复执行；该注解通过 FIRED_TRIGGERS 记录在 Quartz 层拦截并发，
 * 集群多实例下同样生效（共享同一 Quartz 库时）。
 */
@Slf4j
@DisallowConcurrentExecution
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
            Job dynamicJob = batchFactory.createDynamicJob(jobName);
            JobParameters jobParameters = new JobParametersBuilder()
                    .addLong("timestamp", System.currentTimeMillis())
                    .addString("jobName", jobName)
                    .addString("taskName", jobDataMap.getString("taskName"))
                    .addString("serverName", jobDataMap.getString("serverName"))
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
