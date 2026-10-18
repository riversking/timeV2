package com.rivers.moment.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 动态评论表
 * </p>
 *
 * @author rivers
 * @since 2026-10-19
 */
@Data
@Table("moment_comment")
public class MomentComment implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @Id
    private Long id;

    /**
     * 动态ID
     */
    private Long momentId;

    /**
     * 评论人ID
     */
    private String userId;

    /**
     * 评论人名称(冗余)
     */
    private String userName;

    /**
     * 评论内容
     */
    private String content;

    /**
     * 创建人
     */
    private String createUser;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 修改人
     */
    private String updateUser;

    /**
     * 修改时间
     */
    private LocalDateTime updateTime;

    /**
     * 是否删除0.否1.是
     */
    private Integer isDeleted;

}
