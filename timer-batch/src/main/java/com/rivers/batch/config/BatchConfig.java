package com.rivers.batch.config;


import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@EnableBatchProcessing
public class BatchConfig {

    @Bean
    public Job scheduledJob(JobRepository jobRepository, PlatformTransactionManager transactionManage) {
        return new JobBuilder("scheduledJob", jobRepository)
                .start(scheduledStep(jobRepository, transactionManage
                ))
                .build();
    }

    @Bean
    public Step scheduledStep(JobRepository jobRepository, PlatformTransactionManager transactionManage) {
        return new StepBuilder("scheduledStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    return RepeatStatus.FINISHED;
                }, transactionManage)
                .build();
    }

}
