package com.rivers.batch.task;

import com.rivers.core.feign.DynamicClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Slf4j
public class BusinessTasklet implements Tasklet {


    private final DynamicClient dynamicClient;


    public BusinessTasklet(DynamicClient dynamicClient) {
        this.dynamicClient = dynamicClient;
    }

    @Override
    public RepeatStatus execute(@NonNull StepContribution contribution, @NonNull ChunkContext chunkContext) {
        try {
            Map<String, Object> jobParameters = chunkContext.getStepContext().getJobParameters();
            String jobName = (String) jobParameters.get("jobName");
            log.info("Executing business task for job: {}", jobName);
            String result = dynamicClient.executePostApi(jobName, "/api/business/execute", jobParameters);
            ExecutionContext executionContext = contribution.getStepExecution().getExecutionContext();
            executionContext.put("businessTaskId", result);
            return RepeatStatus.FINISHED;
        } catch (Exception e) {
            log.error("Error executing business tasklet", e);
        }
        return RepeatStatus.FINISHED;
    }
}
