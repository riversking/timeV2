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
 * @since 2025-10-31
 */
@EqualsAndHashCode(callSuper = true)
@Data
@TableName("timer_role_menu")
public class TimerRoleMenu extends BasicDO<TimerRoleMenu>{

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 角色code
     */
    @TableField("role_code")
    private String roleCode;

    /**
     * 菜单code
     */
    @TableField("menu_code")
    private String menuCode;
}
