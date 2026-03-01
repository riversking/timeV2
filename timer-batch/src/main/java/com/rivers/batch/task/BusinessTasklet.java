package com.rivers.batch.task;

import com.rivers.core.client.DynamicServiceClient;
import com.rivers.core.entity.JobParamReq;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Slf4j
public class BusinessTasklet implements Tasklet {


    private final DynamicServiceClient dynamicServiceClient;

    public BusinessTasklet(DynamicServiceClient dynamicServiceClient) {
        this.dynamicServiceClient = dynamicServiceClient;
    }


    @Override
    @NullMarked
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        try {
            JobParameters jobParameters = contribution.getStepExecution().getJobParameters();
            String taskName = jobParameters.getString("taskName");
            String serviceName = jobParameters.getString("serviceName");
            log.info("Executing business task for job: {}", taskName);
            log.info("Executing business task for service: {}", serviceName);
            Map<String, Object> parameters = chunkContext.getStepContext().getJobParameters();
            JobParamReq jobParamReq = new JobParamReq();
            jobParamReq.setTaskName(taskName);
            jobParamReq.setParams(parameters);
            dynamicServiceClient.post(serviceName, "/job/execute", jobParamReq, String.class)
                    .subscribe(i -> log.info("Business task executed successfully: {}", i));
            ExecutionContext executionContext = contribution.getStepExecution().getExecutionContext();
            executionContext.put("jobParameters", jobParameters);
            log.info("Execution context: {}", executionContext);
        } catch (Exception e) {
            log.error("Error executing business tasklet", e);
            return RepeatStatus.FINISHED;
        }
        return RepeatStatus.FINISHED;
    }
}
