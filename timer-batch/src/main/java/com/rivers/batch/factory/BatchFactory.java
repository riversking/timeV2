package com.rivers.batch.factory;


import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.rivers.batch.entity.TaskInfo;
import com.rivers.batch.mapper.TaskInfoMapper;
import com.rivers.batch.task.BusinessTasklet;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Service
@EnableBatchProcessing
@Slf4j
public class BatchFactory {

    private final BusinessTasklet businessTasklet;

    private final PlatformTransactionManager transactionManager;

    private final JobRepository jobRepository;

    private final TaskInfoMapper taskInfoMapper;

    // 存储动态创建的Job实例
    private final ConcurrentHashMap<String, Job> dynamicJobs = new ConcurrentHashMap<>();

    // 存储动态创建的Step实例
    private final ConcurrentHashMap<String, Step> dynamicSteps = new ConcurrentHashMap<>();


    public BatchFactory(BusinessTasklet businessTasklet, PlatformTransactionManager transactionManager, JobRepository jobRepository, TaskInfoMapper taskInfoMapper) {
        this.businessTasklet = businessTasklet;
        this.transactionManager = transactionManager;
        this.jobRepository = jobRepository;
        this.taskInfoMapper = taskInfoMapper;
    }

    @PostConstruct
    public void initDynamicJobs() {
        log.info("Initializing dynamic jobs...");
        loadDynamicJobs();
    }

    /**
     * 加载数据库中的所有任务并创建对应的Job和Step
     */
    public void loadDynamicJobs() {
        try {
            // 这里需要从数据库查询所有启用的任务
            // 由于ITaskInfoService没有提供查询所有任务的方法，我们需要添加一个方法
            List<TaskInfo> taskInfos = taskInfoMapper.selectList(Wrappers.emptyWrapper());
            for (TaskInfo taskInfo : taskInfos) {
                createDynamicJobAndStep(taskInfo.getTaskName());
            }
            log.info("Loaded {} dynamic jobs", taskInfos.size());
        } catch (Exception e) {
            log.error("Failed to load dynamic jobs", e);
        }
    }

    /**
     * 根据TaskInfo创建动态Job和Step
     */
    public void createDynamicJobAndStep(String taskName) {
        String stepName = taskName + "Step";
        // 创建Step
        Step step = new StepBuilder(stepName, jobRepository)
                .tasklet(businessTasklet, transactionManager)
                .build();
        dynamicSteps.put(stepName, step);
        // 创建Job
        Job job = new JobBuilder(taskName, jobRepository)
                .start(step)
                .build();
        dynamicJobs.put(taskName, job);
        log.info("Created dynamic job: {} and step: {}", taskName, stepName);
    }

    /**
     * 获取指定名称的动态Job
     */
    public Job getDynamicJob(String jobName) {
        return dynamicJobs.get(jobName);
    }

    /**
     * 获取指定名称的动态Step
     */
    public Step getDynamicStep(String stepName) {
        return dynamicSteps.get(stepName);
    }

    /**
     * 重新加载所有动态Job（当任务信息发生变化时调用）
     */
    public void reloadDynamicJobs() {
        dynamicJobs.clear();
        dynamicSteps.clear();
        loadDynamicJobs();
    }

    /**
     * 添加新的动态Job
     */
    public void addDynamicJob(String taskName) {
        createDynamicJobAndStep(taskName);
    }

    /**
     * 移除动态Job
     */
    public void removeDynamicJob(String jobName) {
        String stepName = jobName + "Step";
        dynamicJobs.remove(jobName);
        dynamicSteps.remove(stepName);
        log.info("Removed dynamic job: {} and step: {}", jobName, stepName);
    }

    // 添加同步辅助方法
    public boolean addDynamicJobIfAbsent(TaskInfo taskInfo) {
        String jobName = taskInfo.getJobName();
        String stepName = jobName + "Step";
        // 使用原子操作避免竞态条件
        return dynamicJobs.putIfAbsent(jobName, createJob(taskInfo, stepName)) == null;
    }

    public Job getOrCreateDynamicJob( String taskName) {
        Job job = dynamicJobs.get(taskName);
        if (job == null) {
            // 双重检查锁定模式
            synchronized (this) {
                job = dynamicJobs.get(taskName);
                if (job == null) {
                    createDynamicJobAndStep(taskName);
                    job = dynamicJobs.get(taskName);
                }
            }
        }
        return job;
    }

    private Job createJob(TaskInfo taskInfo, String stepName) {
        Step step = dynamicSteps.computeIfAbsent(stepName,
                k -> new StepBuilder(k, jobRepository)
                        .tasklet(businessTasklet, transactionManager)
                        .build());
        return new JobBuilder(taskInfo.getTaskName(), jobRepository)
                .start(step)
                .build();
    }

}
