package com.rivers.batch.task;

import com.alibaba.fastjson2.JSONObject;
import com.rivers.core.client.DynamicServiceClient;
import com.rivers.core.entity.JobParamReq;
import com.rivers.core.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
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
        JobParameters jobParameters = contribution.getStepExecution().getJobParameters();
        log.info("Job parameters: {}", jobParameters);
        String taskName = jobParameters.getString("taskName");
        String serviceName = jobParameters.getString("serviceName");
        log.info("Executing business task for job: {}", taskName);
        log.info("Executing business task for service: {}", serviceName);
        Map<String, Object> parameters = chunkContext.getStepContext().getJobParameters();
        JobParamReq jobParamReq = new JobParamReq();
        jobParamReq.setTaskName(taskName);
        jobParamReq.setParams(parameters);
        String res = dynamicServiceClient.post(serviceName, "/job/execute", jobParamReq, String.class).block();
        log.info("Result: {}", res);
        if (StringUtils.isBlank(res)) {
            throw new BusinessException("Result is null");
        }
        JSONObject resultJson = JSONObject.parseObject(res);
        if (resultJson.getInteger("code") != 200) {
            throw new BusinessException("Result is error");
        }
        ExecutionContext executionContext = contribution.getStepExecution().getExecutionContext();
        executionContext.put("jobParameters", jobParameters);
        log.info("Execution context: {}", executionContext);
        return RepeatStatus.FINISHED;
    }
}
