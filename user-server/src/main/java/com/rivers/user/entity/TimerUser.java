package com.rivers.user.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.rivers.core.entity.BasicDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.io.Serializable;

/**
 * <p>
 *
 * </p>
 *
 * @author xx
 * @since 2025-10-07
 */
@EqualsAndHashCode(callSuper = true)
@Data
@TableName("timer_user")
public class TimerUser extends BasicDO<TimerUser> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 员工号
     */
    @TableField("user_id")
    private String userId;

    /**
     * 用户名
     */
    @TableField("username")
    private String username;

    /**
     * 密码
     */
    @TableField("password")
    private String password;

    /**
     * 简介
     */
    @TableField("phone")
    private String phone;

    /**
     * 头像
     */
    @TableField("avatar")
    private String avatar;

    /**
     * 邮箱
     */
    @TableField("mail")
    private String mail;

    /**
     * 昵称
     */
    @TableField("nickname")
    private String nickname;

    /**
     * 0-有效，1-失效
     */
    @TableField("is_disable")
    private Integer isDisable;
}
