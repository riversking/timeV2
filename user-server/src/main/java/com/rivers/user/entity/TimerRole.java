package com.rivers.user.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 角色表（R2DBC 映射）
 *
 * @author xx
 * @since 2025-10-10
 */
@Data
@Table("timer_role")
public class TimerRole implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    private Long id;

    /**
     * 角色名称
     */
    private String roleName = "";

    /**
     * 角色编码
     */
    private String roleCode = "";

    private String createUser = "";

    private LocalDateTime createTime = LocalDateTime.now();

    private String updateUser = "";

    private LocalDateTime updateTime = LocalDateTime.now();

    private Integer isDeleted = 0;
}
