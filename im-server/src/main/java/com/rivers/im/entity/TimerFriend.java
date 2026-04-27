package com.rivers.im.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 好友关系表
 * </p>
 *
 * @author
 * @since 2026-04-28
 */
@Data
@Table("timer_friend")
public class TimerFriend implements Serializable {

    @Serial
    private static final long serialVersionUID = 1273140946938184191L;

    @Id
    private Long id;

    /**
     * 用户ID
     */
    @Column("user_id")
    private Long userId;

    /**
     * 好友ID
     */
    @Column("friend_id")
    private Long friendId;

    /**
     * 备注名
     */
    @Column("remark")
    private String remark;

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
