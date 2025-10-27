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
 * 菜单表
 * </p>
 *
 * @author xx
 * @since 2025-10-23
 */
@EqualsAndHashCode(callSuper = true)
@Data
@TableName("timer_menu")
public class TimerMenu extends BasicDO<TimerMenu> {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 父级菜单ID，0表示顶级菜单
     */
    @TableField("parent_id")
    private Long parentId;

    /**
     * 菜单名称
     */
    @TableField("menu_name")
    private String menuName;

    /**
     * 菜单编码
     */
    @TableField("menu_code")
    private String menuCode;

    /**
     * 菜单类型：1-目录，2-菜单，3-按钮
     */
    @TableField("menu_type")
    private Integer menuType;

    /**
     * 路由路径
     */
    @TableField("route_path")
    private String routePath;

    /**
     * 菜单图标
     */
    @TableField("icon")
    private String icon;

    /**
     * 权限标识
     */
    @TableField("permission_code")
    private String permissionCode;

    /**
     * 排序号
     */
    @TableField("sort_order")
    private Integer sortOrder;

    /**
     * 状态：1-启用，0-禁用
     */
    @TableField("status")
    private Integer status;

}
