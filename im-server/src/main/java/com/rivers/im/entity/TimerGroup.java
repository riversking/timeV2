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
 * 群组表
 * </p>
 *
 * @author
 * @since 2026-04-28
 */
@Data
@Table("timer_group")
public class TimerGroup implements Serializable {

    @Serial
    private static final long serialVersionUID = 2615705232078767320L;

    @Id
    private Long id;

    /**
     * 群组名称
     */
    @Column("name")
    private String name;

    /**
     * 群头像
     */
    @Column("avatar")
    private String avatar;

    /**
     * 群描述
     */
    @Column("description")
    private String description;

    /**
     * 最大成员数
     */
    @Column("max_members")
    private Integer maxMembers;

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
