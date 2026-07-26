package com.rivers.batch.service;

import com.rivers.batch.entity.TaskExecuteLog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class TaskLogSaveService {

    @Async
    public void saveAsync(TaskExecuteLog entity) {
        try {
            entity.insert();
        } catch (Exception e) {
            log.error("Failed to save task execution log for task: {}", entity.getTaskName(), e);
        }
    }
}