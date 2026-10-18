package com.rivers.moment.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 用户动态表
 * </p>
 *
 * @author rivers
 * @since 2026-10-17
 */
@Data
@Table("moment")
public class Moment implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @Id
    private Long id;

    /**
     * 动态正文
     */
    private String content;

    /**
     * 球队社区ID(nba-server teamId,0=未归属)
     */
    private Integer teamId;

    /**
     * 点赞数(冗余)
     */
    private Integer likeCount;

    /**
     * 评论数(冗余)
     */
    private Integer commentCount;

    /**
     * 发布人ID
     */
    private String userId;

    /**
     * 发布人名称(冗余)
     */
    private String userName;

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
