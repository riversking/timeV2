package com.rivers.moment.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 动态图片表
 * </p>
 *
 * @author rivers
 * @since 2026-10-17
 */
@Data
@Table("moment_image")
public class MomentImage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @Id
    private Long id;

    /**
     * 所属动态ID
     */
    private Long momentId;

    /**
     * 图片URL
     */
    private String url;

    /**
     * 展示顺序(从0开始)
     */
    private Integer sort;

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
