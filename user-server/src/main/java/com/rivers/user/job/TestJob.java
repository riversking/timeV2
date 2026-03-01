package com.rivers.user.job;

import com.rivers.core.entity.JobParamReq;
import com.rivers.core.task.BusinessTaskHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component("testJob")
@Slf4j
public class TestJob extends BusinessTaskHandler {

    @Override
    protected void doExecute(JobParamReq jobParamReq) {
        log.info("测试任务开始执行");
    }
}
