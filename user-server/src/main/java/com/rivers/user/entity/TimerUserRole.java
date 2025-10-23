package com.rivers.user.entity;

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
 * @since 2025-10-22
 */
@EqualsAndHashCode(callSuper = true)
@Data
@TableName("timer_user_role")
public class TimerUserRole extends BasicDO<TimerUserRole>{

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 角色code
     */
    @TableField("role_code")
    private String roleCode;

    /**
     * 樱花账号
     */
    @TableField("user_id")
    private String userId;
}
