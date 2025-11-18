package com.rivers.batch.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.rivers.batch.entity.TaskInfo;
import com.rivers.batch.mapper.TaskInfoMapper;
import com.rivers.batch.quartz.BatchQuartzJob;
import com.rivers.batch.service.ITaskInfoService;
import com.rivers.core.exception.BusinessException;
import com.rivers.core.vo.ResultVO;
import com.rivers.proto.LoginUser;
import com.rivers.proto.SaveTaskInfoReq;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.quartz.*;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class TaskInfoService implements ITaskInfoService {

    private final TaskInfoMapper taskInfoMapper;

    private final Scheduler scheduler;

    public TaskInfoService(TaskInfoMapper taskInfoMapper, Scheduler scheduler) {
        this.taskInfoMapper = taskInfoMapper;
        this.scheduler = scheduler;
    }

    @Override
    public ResultVO<Void> saveTaskInfo(SaveTaskInfoReq saveTaskInfoReq) {
        String taskName = saveTaskInfoReq.getTaskName();
        String jobName = saveTaskInfoReq.getJobName();
        String cron = saveTaskInfoReq.getCron();
        String serviceName = saveTaskInfoReq.getServiceName();
        if (StringUtils.isBlank(taskName)) {
            return ResultVO.fail("任务名称不能为空");
        }
        if (StringUtils.isBlank(jobName)) {
            return ResultVO.fail("定时任务名称不能为空");
        }
        if (StringUtils.isBlank(cron)) {
            return ResultVO.fail("cron表达式不能为空");
        }
        if (StringUtils.isBlank(serviceName)) {
            return ResultVO.fail("服务名称不能为空");
        }
        LambdaQueryWrapper<TaskInfo> taskWrapper = Wrappers.lambdaQuery();
        taskWrapper.eq(TaskInfo::getTaskName, taskName)
                .eq(TaskInfo::getServiceName, serviceName);
        Long count = taskInfoMapper.selectCount(taskWrapper);
        if (count > 0) {
            return ResultVO.fail("任务名称已存在");
        }
        LoginUser loginUser = saveTaskInfoReq.getLoginUser();
        String userId = loginUser.getUserId();
        TaskInfo taskInfo = new TaskInfo();
        taskInfo.setTaskName(taskName);
        taskInfo.setJobName(jobName);
        taskInfo.setServiceName(serviceName);
        taskInfo.setCron(cron);
        taskInfo.setCreateUser(userId);
        taskInfo.setUpdateUser(userId);
        taskInfo.insert();
        addNewJob(jobName, cron);
        return ResultVO.ok();
    }

    public void addNewJob(String jobName, String cronExpression) {
        // 创建JobDetail
        JobDetail jobDetail = JobBuilder.newJob(BatchQuartzJob.class)
                .withIdentity(jobName)
                .withDescription("Manual added job")
                .storeDurably()
                .build();
        // 创建Trigger
        CronTrigger trigger = TriggerBuilder.newTrigger()
                .forJob(jobDetail)
                .withIdentity(jobName + "Trigger")
                .withSchedule(CronScheduleBuilder.cronSchedule(cronExpression))
                .build();
        // 调度任务
        try {
            scheduler.scheduleJob(jobDetail, trigger);
        } catch (SchedulerException e) {
            throw new BusinessException(e);
        }
    }
}
