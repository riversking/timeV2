package com.rivers.im.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 
 * </p>
 *
 * @author 
 * @since 2026-04-28
 */
@Getter
@Setter
@Table("timer_user")
public class TimerUser implements Serializable{

    @Serial
    private static final long serialVersionUID = -6070327895080852854L;

    @Id
    private Long id;

    /**
     * 用户id
     */
    @Column("user_id")
    private String userId;

    /**
     * 用户名
     */
    @Column("username")
    private String username;

    /**
     * 密码
     */
    @Column("password")
    private String password;

    /**
     * 手机
     */
    @Column("phone")
    private String phone;

    /**
     * 头像
     */
    @Column("avatar")
    private String avatar;

    /**
     * 邮箱
     */
    @Column("mail")
    private String mail;

    /**
     * 用户编码
     */
    @Column("user_code")
    private String userCode;

    /**
     * 是否禁用 0.否 1.是 
     */
    @Column("is_disable")
    private String isDisable;

    /**
     * 创建人
     */
    @Column("create_user")
    private String createUser;

    /**
     * 创建时间
     */
    @Column("create_time")
    private LocalDateTime createTime;

    /**
     * 修改人
     */
    @Column("update_user")
    private String updateUser;

    /**
     * 修改时间
     */
    @Column("update_time")
    private LocalDateTime updateTime;

    /**
     * 是否删除
     */
    @Column("is_deleted")
    private Integer isDeleted;

}
