package com.rivers.batch.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rivers.batch.entity.TaskInfo;
import com.rivers.batch.mapper.TaskInfoMapper;
import com.rivers.batch.quartz.BatchQuartzJob;
import com.rivers.batch.service.ITaskInfoService;
import com.rivers.batch.vo.JobRunTimeVO;
import com.rivers.core.exception.BusinessException;
import com.rivers.core.vo.ResultVO;
import com.rivers.proto.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.quartz.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @author xx
 */
@Service
@Slf4j
public class TaskInfoServiceImpl implements ITaskInfoService {

    private final TaskInfoMapper taskInfoMapper;

    private final Scheduler scheduler;

    private final JdbcTemplate jdbcTemplate;


    public TaskInfoServiceImpl(TaskInfoMapper taskInfoMapper, Scheduler scheduler, JdbcTemplate jdbcTemplate) {
        this.taskInfoMapper = taskInfoMapper;
        this.scheduler = scheduler;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResultVO<Void> saveTaskInfo(SaveTaskInfoReq saveTaskInfoReq) {
        String taskName = saveTaskInfoReq.getTaskName();
        String jobName = saveTaskInfoReq.getJobName();
        String cron = saveTaskInfoReq.getCron();
        String serverName = saveTaskInfoReq.getServerName();
        if (StringUtils.isBlank(taskName)) {
            return ResultVO.fail("任务名称不能为空");
        }
        if (StringUtils.isBlank(jobName)) {
            return ResultVO.fail("定时任务名称不能为空");
        }
        if (StringUtils.isBlank(cron)) {
            return ResultVO.fail("cron表达式不能为空");
        }
        if (StringUtils.isBlank(serverName)) {
            return ResultVO.fail("服务名称不能为空");
        }
        LambdaQueryWrapper<TaskInfo> taskWrapper = Wrappers.lambdaQuery();
        taskWrapper.eq(TaskInfo::getTaskName, taskName)
                .eq(TaskInfo::getServerName, serverName);
        Long count = taskInfoMapper.selectCount(taskWrapper);
        if (count > 0) {
            return ResultVO.fail("任务名称已存在");
        }
        LoginUser loginUser = saveTaskInfoReq.getLoginUser();
        String userId = loginUser.getUserId();
        TaskInfo taskInfo = new TaskInfo();
        taskInfo.setTaskName(taskName);
        taskInfo.setJobName(jobName);
        taskInfo.setServerName(serverName);
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
        String serverName = updateTaskInfoReq.getServerName();
        if (StringUtils.isBlank(taskName)) {
            return ResultVO.fail("任务名称不能为空");
        }
        if (StringUtils.isBlank(jobName)) {
            return ResultVO.fail("定时任务名称不能为空");
        }
        if (StringUtils.isBlank(cron)) {
            return ResultVO.fail("cron表达式不能为空");
        }
        if (StringUtils.isBlank(serverName)) {
            return ResultVO.fail("服务名称不能为空");
        }
        long id = updateTaskInfoReq.getId();
        TaskInfo oldTask = taskInfoMapper.selectById(id);
        if (oldTask == null) {
            return ResultVO.fail("任务不存在");
        }
        LambdaQueryWrapper<TaskInfo> taskWrapper = Wrappers.lambdaQuery();
        taskWrapper.eq(TaskInfo::getTaskName, taskName)
                .eq(TaskInfo::getServerName, serverName);
        TaskInfo taskInfo = taskInfoMapper.selectOne(taskWrapper);
        if (taskInfo != null && taskInfo.getId() != id) {
            return ResultVO.fail("任务名称已存在");
        }
        LoginUser loginUser = updateTaskInfoReq.getLoginUser();
        TaskInfo task = new TaskInfo();
        task.setId(id);
        task.setTaskName(taskName);
        task.setJobName(jobName);
        task.setServerName(serverName);
        task.setCron(cron);
        task.setEmail(updateTaskInfoReq.getEmail());
        task.setUpdateUser(loginUser.getUserId());
        task.updateById();
        try {
            scheduler.deleteJob(JobKey.jobKey(oldTask.getTaskName()));
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
        TaskInfo taskInfo = taskInfoMapper.selectById(id);
        Optional.ofNullable(taskInfo).ifPresent(i -> {
            try {
                scheduler.triggerJob(JobKey.jobKey(i.getTaskName()));
            } catch (SchedulerException e) {
                throw new BusinessException(e);
            }
        });
        return ResultVO.ok();
    }

    @Override
    public ResultVO<JobPageRes> getJobPage(JobPageReq jobPageReq) {
        long currentPage = jobPageReq.getCurrentPage();
        long pageSize = jobPageReq.getPageSize();
        Page<TaskInfo> page = Page.of(currentPage, pageSize);
        String jobName = jobPageReq.getJobName();
        LambdaQueryWrapper<TaskInfo> taskWrapper = Wrappers.lambdaQuery();
        taskWrapper.eq(StringUtils.isNotBlank(jobName), TaskInfo::getJobName, jobName);
        IPage<TaskInfo> taskInfoPage = taskInfoMapper.selectPage(page, taskWrapper);
        long total = taskInfoPage.getTotal();
        List<TaskInfo> records = taskInfoPage.getRecords();
        if (total == 0L) {
            return ResultVO.ok(JobPageRes.newBuilder().setTotal(total).build());
        }
        List<String> jobNames = records.stream()
                .map(TaskInfo::getTaskName)
                .toList();
        String sql = "SELECT A.* FROM (SELECT B.JOB_NAME,A.STATUS,A.LAST_UPDATED FROM BATCH_JOB_EXECUTION A " +
                "LEFT JOIN BATCH_JOB_INSTANCE B ON A.JOB_INSTANCE_ID=B.JOB_INSTANCE_ID " +
                "WHERE B.JOB_NAME IN (:jobName)) A " +
                "INNER JOIN (SELECT B.JOB_NAME,MAX(A.LAST_UPDATED) AS LAST_UPDATED FROM BATCH_JOB_EXECUTION A " +
                "LEFT JOIN BATCH_JOB_INSTANCE B ON A.JOB_INSTANCE_ID=B.JOB_INSTANCE_ID " +
                "WHERE B.JOB_NAME IN (:jobName) GROUP BY B.JOB_NAME) B ON A.JOB_NAME=B.JOB_NAME" +
                " AND A.LAST_UPDATED=B.LAST_UPDATED";
        NamedParameterJdbcTemplate namedJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("jobName", jobNames);
        List<JobRunTimeVO> jobRunTimes = namedJdbcTemplate.query(sql, params, new JobRunTimeRowMapper());
        Map<String, JobRunTimeVO> jobRunTimeMap = jobRunTimes.stream()
                .collect(Collectors.toMap(JobRunTimeVO::getJobName, v -> v));
        List<JobDetailRes> jobs = records.stream()
                .map(i ->
                        JobDetailRes.newBuilder()
                                .setId(i.getId())
                                .setTaskName(i.getTaskName())
                                .setJobName(i.getJobName())
                                .setServerName(i.getServerName())
                                .setCron(i.getCron())
                                .setEmail(i.getEmail())
                                .setStatus(i.getStatus())
                                .setCreateTime(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                                        .format(i.getCreateTime()))
                                .setLastRunTime(Optional.ofNullable(jobRunTimeMap.get(i.getTaskName()))
                                        .map(t -> DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                                                .format(t.getLastUpdated()))
                                        .orElse(""))
                                .setRunStatus(Optional.ofNullable(jobRunTimeMap.get(i.getTaskName()))
                                        .map(JobRunTimeVO::getStatus)
                                        .orElse(""))
                                .build())
                .toList();
        return ResultVO.ok(JobPageRes.newBuilder()
                .addAllJobs(jobs)
                .setTotal(total)
                .build());
    }

    @Override
    public ResultVO<JobDetailRes> getJobDetail(CommonTaskReq commonTaskReq) {
        long id = commonTaskReq.getId();
        TaskInfo taskInfo = taskInfoMapper.selectById(id);
        JobDetailRes jobDetailRes = Optional.ofNullable(taskInfo)
                .map(i -> {
                    return JobDetailRes.newBuilder()
                            .setId(taskInfo.getId())
                            .setTaskName(taskInfo.getTaskName())
                            .setJobName(taskInfo.getJobName())
                            .setServerName(taskInfo.getServerName())
                            .setCron(taskInfo.getCron())
                            .setEmail(taskInfo.getEmail())
                            .setStatus(taskInfo.getStatus())
                            .build();
                }).orElse(JobDetailRes.newBuilder().build());
        return ResultVO.ok(jobDetailRes);
    }

    private void addNewJob(TaskInfo taskInfo) {
        // 创建JobDetail
        JobDetail jobDetail = JobBuilder.newJob(BatchQuartzJob.class)
                .withIdentity(taskInfo.getTaskName())
                .usingJobData("batchJobName", taskInfo.getJobName())
                .withDescription("Manual added job")
                .storeDurably()
                .build();
        jobDetail.getJobDataMap().put("taskName", taskInfo.getTaskName());
        jobDetail.getJobDataMap().put("serverName", taskInfo.getServerName());
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

    private static class JobRunTimeRowMapper implements RowMapper<JobRunTimeVO> {
        @Override
        public JobRunTimeVO mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new JobRunTimeVO(
                    rs.getString("JOB_NAME"),
                    rs.getString("STATUS"),
                    rs.getTimestamp("LAST_UPDATED") != null ?
                            rs.getTimestamp("LAST_UPDATED").toLocalDateTime() : null
            );
        }
    }
}
