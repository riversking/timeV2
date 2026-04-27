package com.rivers.im.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Date;

/**
 * <p>
 * 群成员表
 * </p>
 *
 * @author
 * @since 2026-04-28
 */
@Getter
@Setter
@Table("timer_group_member")
public class TimerGroupMember implements Serializable {

    @Serial
    private static final long serialVersionUID = 6220782195980410412L;

    @Id
    private Long id;

    /**
     * 群组ID
     */
    @Column("group_id")
    private Long groupId;

    /**
     * 用户ID
     */
    @Column("user_id")
    private Long userId;

    /**
     * 角色: 1-普通成员, 2-管理员, 3-群主
     */
    @Column("role")
    private Byte role;

    /**
     * 群内昵称
     */
    @Column("nickname")
    private String nickname;

    /**
     * 加入时间
     */
    @Column("joined_at")
    private Date joinedAt;

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
