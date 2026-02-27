package com.rivers.batch.factory;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动态批处理任务工厂，支持从数据库动态加载和管理任务
 */
@Service
@EnableBatchProcessing
@Slf4j
public class BatchFactory {

    private final BusinessTasklet businessTasklet;
    private final JobRepository jobRepository;
    private final TaskInfoMapper taskInfoMapper;
    private final PlatformTransactionManager transactionManager;

    // 存储动态创建的Job实例 (key: taskName)
    private final Map<String, Job> dynamicJobs = new ConcurrentHashMap<>();
    // 存储动态创建的Step实例 (key: taskName + "Step")
    private final Map<String, Step> dynamicSteps = new ConcurrentHashMap<>();

    public BatchFactory(
            BusinessTasklet businessTasklet,
            JobRepository jobRepository,
            TaskInfoMapper taskInfoMapper,
            PlatformTransactionManager transactionManager) {
        this.businessTasklet = businessTasklet;
        this.jobRepository = jobRepository;
        this.taskInfoMapper = taskInfoMapper;
        this.transactionManager = transactionManager;
    }

    @PostConstruct
    public void initDynamicJobs() {
        log.info("Initializing dynamic batch jobs...");
        try {
            loadDynamicJobs();
            log.info("Successfully loaded {} dynamic batch jobs", dynamicJobs.size());
        } catch (Exception e) {
            log.error("Failed to initialize dynamic batch jobs", e);
            throw new IllegalStateException("Batch job initialization failed", e);
        }
    }

    /**
     * 从数据库加载所有启用的任务并创建对应的Job和Step
     */
    private void loadDynamicJobs() {
        LambdaQueryWrapper<TaskInfo> taskWrapper = Wrappers.lambdaQuery(TaskInfo.class)
                .eq(TaskInfo::getStatus, 1); // 仅加载启用状态的任务
        List<TaskInfo> taskInfos = taskInfoMapper.selectList(taskWrapper);
        log.info("Found {} enabled tasks in database", taskInfos.size());
        for (TaskInfo taskInfo : taskInfos) {
            createDynamicJob(taskInfo.getTaskName());
        }
    }

    /**
     * 创建动态Job (确保Step只创建一次)
     */
    public Job createDynamicJob(String taskName) {
        String stepName = taskName + "Step";
        // Step 创建方式 (Spring Batch 5.0.0 新方式)
        Step step = dynamicSteps.computeIfAbsent(stepName, name ->
                new StepBuilder(name, jobRepository)
                        .tasklet(businessTasklet,transactionManager)
                        .build()
        );
        // Job 创建方式 (Spring Batch 5.0.0 新方式)
        return dynamicJobs.computeIfAbsent(taskName, name ->
                new JobBuilder(name, jobRepository)
                        .start(step)
                        .build()
        );
    }

    /**
     * 获取指定名称的动态Job
     */
    public Job getDynamicJob(String taskName) {
        return dynamicJobs.get(taskName);
    }

    /**
     * 重新加载所有动态Job (当任务配置变更时调用)
     */
    public void reloadDynamicJobs() {
        dynamicJobs.clear();
        dynamicSteps.clear();
        initDynamicJobs(); // 重新初始化
    }

    /**
     * 添加新的动态Job (原子操作)
     */
    public boolean addDynamicJob(String taskName) {
        return dynamicJobs.putIfAbsent(taskName, createDynamicJob(taskName)) == null;
    }

    /**
     * 移除动态Job
     */
    public void removeDynamicJob(String taskName) {
        String stepName = taskName + "Step";
        // 移除Job和对应的Step
        dynamicJobs.remove(taskName);
        dynamicSteps.remove(stepName);
        log.info("Removed dynamic job: {} and step: {}", taskName, stepName);
    }
}