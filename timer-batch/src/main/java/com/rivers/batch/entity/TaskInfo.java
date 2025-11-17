package com.rivers.batch.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.rivers.core.entity.BasicDO;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

/**
 * <p>
 * 
 * </p>
 *
 * @author xx
 * @since 2025-11-17
 */
@EqualsAndHashCode(callSuper = true)
@Data
@TableName("task_info")
public class TaskInfo extends BasicDO<TaskInfo>{

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 任务bean
     */
    @TableField("task_name")
    private String taskName;

    /**
     * 任务名称
     */
    @TableField("job_name")
    private String jobName;

    /**
     * 服务名称
     */
    @TableField("service_name")
    private String serviceName;

    /**
     * cron表达式
     */
    @TableField("cron")
    private String cron;

    /**
     * 告警邮箱
     */
    @TableField("email")
    private String email;
}
