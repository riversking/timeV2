package com.rivers.batch.config;


import com.rivers.batch.task.BusinessTasklet;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@EnableBatchProcessing
public class BatchConfig {

    private final BusinessTasklet businessTasklet;

    public BatchConfig(BusinessTasklet businessTasklet) {
        this.businessTasklet = businessTasklet;
    }

    @Bean
    public Job businessJob(JobRepository jobRepository, PlatformTransactionManager transactionManage) {
        return new JobBuilder("businessJob", jobRepository)
                .start(businessStep(jobRepository, transactionManage))
                .build();
    }

    @Bean
    public Step businessStep(JobRepository jobRepository, PlatformTransactionManager transactionManage) {
        return new StepBuilder("businessStep", jobRepository)
                .tasklet(businessTasklet, transactionManage)
                .build();
    }

}
