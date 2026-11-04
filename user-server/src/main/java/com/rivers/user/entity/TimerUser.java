package com.rivers.user.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户表（R2DBC 映射，非空列给出与库一致的默认值，避免整行插入时写 NULL）
 *
 * @author xx
 * @since 2025-10-07
 */
@Data
@Table("timer_user")
public class TimerUser implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    private Long id;

    /**
     * 员工号
     */
    private String userId = "";

    /**
     * 用户名
     */
    private String username = "";

    /**
     * 密码
     */
    private String password = "";

    /**
     * 手机号
     */
    private String phone = "";

    /**
     * 头像
     */
    private String avatar = "";

    /**
     * 邮箱
     */
    private String mail = "";

    /**
     * 用户编码
     */
    private String userCode = "";

    /**
     * 0-有效，1-失效
     */
    private String isDisable = "0";

    private String createUser = "";

    private LocalDateTime createTime = LocalDateTime.now();

    private String updateUser = "";

    private LocalDateTime updateTime = LocalDateTime.now();

    private Integer isDeleted = 0;
}
