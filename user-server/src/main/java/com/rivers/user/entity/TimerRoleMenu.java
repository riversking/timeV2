package com.rivers.user.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 角色-菜单关联（R2DBC 映射）
 *
 * @author xx
 * @since 2025-10-31
 */
@Data
@Table("timer_role_menu")
public class TimerRoleMenu implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    private Long id;

    /**
     * 角色code
     */
    private String roleCode = "";

    /**
     * 菜单code
     */
    private String menuCode = "";

    private String createUser = "";

    private LocalDateTime createTime = LocalDateTime.now();

    private String updateUser = "";

    private LocalDateTime updateTime = LocalDateTime.now();

    private Integer isDeleted = 0;
}
