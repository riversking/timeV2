package com.rivers.batch.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.rivers.batch.entity.TaskInfo;
import com.rivers.batch.mapper.TaskInfoMapper;
import com.rivers.batch.quartz.BatchQuartzJob;
import com.rivers.batch.service.ITaskInfoService;
import com.rivers.core.exception.BusinessException;
import com.rivers.core.vo.ResultVO;
import com.rivers.proto.CommonTaskReq;
import com.rivers.proto.LoginUser;
import com.rivers.proto.SaveTaskInfoReq;
import com.rivers.proto.UpdateTaskInfoReq;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.quartz.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

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
    @Transactional(rollbackFor = Exception.class)
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
        addNewJob(taskInfo);
        return ResultVO.ok();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResultVO<Void> updateTaskInfo(UpdateTaskInfoReq updateTaskInfoReq) {
        String taskName = updateTaskInfoReq.getTaskName();
        String jobName = updateTaskInfoReq.getJobName();
        String cron = updateTaskInfoReq.getCron();
        String serviceName = updateTaskInfoReq.getServiceName();
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
        long id = updateTaskInfoReq.getId();
        LambdaQueryWrapper<TaskInfo> taskWrapper = Wrappers.lambdaQuery();
        taskWrapper.eq(TaskInfo::getTaskName, taskName)
                .eq(TaskInfo::getServiceName, serviceName);
        TaskInfo taskInfo = taskInfoMapper.selectOne(taskWrapper);
        if (taskInfo != null && taskInfo.getId() != id) {
            return ResultVO.fail("任务名称已存在");
        }
        TaskInfo task = new TaskInfo();
        task.setId(id);
        task.setTaskName(taskName);
        task.setJobName(jobName);
        task.setServiceName(serviceName);
        task.setCron(cron);
        task.setEmail(updateTaskInfoReq.getEmail());
        task.updateById();
        try {
            scheduler.deleteJob(JobKey.jobKey(taskName));
        } catch (SchedulerException e) {
            throw new BusinessException("", e);
        }
        addNewJob(task);
        return ResultVO.ok();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResultVO<Void> pauseTask(CommonTaskReq commonTaskReq) {
        long id = commonTaskReq.getId();
        LoginUser loginUser = commonTaskReq.getLoginUser();
        String userId = loginUser.getUserId();
        TaskInfo taskInfo = taskInfoMapper.selectById(id);
        Optional.ofNullable(taskInfo).ifPresent(i -> {
            taskInfo.setUpdateUser(userId);
            taskInfo.setStatus("0");
            taskInfo.updateById();
            try {
                scheduler.pauseJob(JobKey.jobKey(i.getTaskName()));
            } catch (SchedulerException e) {
                throw new BusinessException(e);
            }
        });
        return ResultVO.ok();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResultVO<Void> resumeTask(CommonTaskReq commonTaskReq) {
        long id = commonTaskReq.getId();
        LoginUser loginUser = commonTaskReq.getLoginUser();
        String userId = loginUser.getUserId();
        TaskInfo taskInfo = taskInfoMapper.selectById(id);
        Optional.ofNullable(taskInfo).ifPresent(i -> {
            i.setUpdateUser(userId);
            i.setStatus("1");
            i.updateById();
            try {
                scheduler.resumeJob(JobKey.jobKey(i.getTaskName()));
            } catch (SchedulerException e) {
                throw new BusinessException(e);
            }
        });
        return ResultVO.ok();
    }

    @Override
    public ResultVO<Void> deleteTask(CommonTaskReq commonTaskReq) {
        long id = commonTaskReq.getId();
        TaskInfo taskInfo = taskInfoMapper.selectById(id);
        Optional.ofNullable(taskInfo).ifPresent(i -> {
            i.deleteById();
            try {
                scheduler.deleteJob(JobKey.jobKey(i.getTaskName()));
            } catch (SchedulerException e) {
                throw new BusinessException(e);
            }
        });
        return ResultVO.ok();
    }

    @Override
    public ResultVO<Void> runTask(CommonTaskReq commonTaskReq) {
        long id = commonTaskReq.getId();
        LoginUser loginUser = commonTaskReq.getLoginUser();
        String userId = loginUser.getUserId();
        TaskInfo taskInfo = taskInfoMapper.selectById(id);
        Optional.ofNullable(taskInfo).ifPresent(i -> {
            i.setUpdateUser(userId);
            i.setStatus("1");
            i.updateById();
            try {
                scheduler.triggerJob(JobKey.jobKey(i.getTaskName()));
            } catch (SchedulerException e) {
                throw new BusinessException(e);
            }
        });
        return ResultVO.ok();
    }

    public void addNewJob(TaskInfo taskInfo) {
        // 创建JobDetail
        JobDetail jobDetail = JobBuilder.newJob(BatchQuartzJob.class)
                .withIdentity(taskInfo.getTaskName())
                .usingJobData("batchJobName", taskInfo.getJobName())
                .withDescription("Manual added job")
                .storeDurably()
                .build();
        jobDetail.getJobDataMap().put("taskName", taskInfo.getTaskName());
        jobDetail.getJobDataMap().put("serviceName", taskInfo.getServiceName());
        jobDetail.getJobDataMap().put("alarmEmail", taskInfo.getEmail());
        // 创建Trigger
        CronTrigger trigger = TriggerBuilder.newTrigger()
                .forJob(jobDetail)
                .withIdentity(taskInfo.getTaskName() + "_Trigger")
                .withSchedule(CronScheduleBuilder.cronSchedule(taskInfo.getCron()))
                .build();
        // 调度任务
        try {
            scheduler.scheduleJob(jobDetail, trigger);
        } catch (SchedulerException e) {
            throw new BusinessException("Failed to schedule task", e);
        }
    }
}
