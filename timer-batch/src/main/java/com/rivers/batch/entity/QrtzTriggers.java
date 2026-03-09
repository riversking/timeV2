package com.rivers.user.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.rivers.core.entity.BasicDO;
import java.io.Serializable;
import lombok.Getter;
import lombok.Setter;

/**
 * <p>
 * 
 * </p>
 *
 * @author xx
 * @since 2026-03-09
 */
@Getter
@Setter
@TableName("QRTZ_TRIGGERS")
public class QrtzTriggers extends BasicDO<QrtzTriggers>{

    private static final long serialVersionUID = 1L;
    @TableId("SCHED_NAME")
    private String schedName;
    @TableId("TRIGGER_NAME")
    private String triggerName;
    @TableId("TRIGGER_GROUP")
    private String triggerGroup;
    @TableField("JOB_NAME")
    private String jobName;
    @TableField("JOB_GROUP")
    private String jobGroup;
    @TableField("DESCRIPTION")
    private String DESCRIPTION;
    @TableField("NEXT_FIRE_TIME")
    private Long nextFireTime;
    @TableField("PREV_FIRE_TIME")
    private Long prevFireTime;
    @TableField("PRIORITY")
    private Integer PRIORITY;
    @TableField("TRIGGER_STATE")
    private String triggerState;
    @TableField("TRIGGER_TYPE")
    private String triggerType;
    @TableField("START_TIME")
    private Long startTime;
    @TableField("END_TIME")
    private Long endTime;
    @TableField("CALENDAR_NAME")
    private String calendarName;
    @TableField("MISFIRE_INSTR")
    private Short misfireInstr;
    @TableField("JOB_DATA")
    private byte[] jobData;

    @Override
    public Serializable pkVal() {
        return this.triggerGroup;
    }
}
