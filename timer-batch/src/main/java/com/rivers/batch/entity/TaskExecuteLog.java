package com.rivers.batch.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.rivers.core.entity.BasicDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("task_execute_log")
public class TaskExecuteLog extends BasicDO<TaskExecuteLog> {

    /**
     * 任务名称
     */
    @TableField("task_name")
    private String taskName;

    /**
     * 定时任务名称
     */
    @TableField("job_name")
    private String jobName;

    /**
     * 目标服务名
     */
    @TableField("server_name")
    private String serverName;

    /**
     * 执行器IP
     */
    @TableField("executor_ip")
    private String executorIp;

    /**
     * 目标服务IP（多个逗号分隔）
     */
    @TableField("target_ip")
    private String targetIp;

    /**
     * 执行耗时(ms)
     */
    @TableField("execute_time")
    private Long executeTime;

    /**
     * 执行状态: SUCCESS / FAILED
     */
    @TableField("status")
    private String status;

    /**
     * 错误信息
     */
    @TableField("error_msg")
    private String errorMsg;

    /**
     * 触发类型: quartz / manual
     */
    @TableField("trigger_type")
    private String triggerType;
}