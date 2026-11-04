package com.rivers.user.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 菜单表（R2DBC 映射）
 *
 * @author xx
 * @since 2025-10-23
 */
@Data
@Table("timer_menu")
public class TimerMenu implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    private Long id;

    /**
     * 父级菜单ID，0表示顶级菜单
     */
    private Long parentId = 0L;

    /**
     * 菜单名称
     */
    private String menuName = "";

    /**
     * 菜单编码
     */
    private String menuCode = "";

    /**
     * 菜单类型：1-目录，2-菜单，3-按钮
     */
    private Integer menuType = 1;

    /**
     * 菜单归属：1-后台管理，2-门户
     */
    private Integer menuScope = 1;

    /**
     * 路由路径
     */
    private String routePath = "";

    /**
     * 菜单图标
     */
    private String icon = "";

    /**
     * 权限标识
     */
    private String permissionCode = "";

    /**
     * 排序号
     */
    private Integer sortOrder = 0;

    /**
     * 状态：1-启用，0-禁用
     */
    private Integer status = 1;

    private String component = "";

    private String createUser = "";

    private LocalDateTime createTime = LocalDateTime.now();

    private String updateUser = "";

    private LocalDateTime updateTime = LocalDateTime.now();

    private Integer isDeleted = 0;
}
