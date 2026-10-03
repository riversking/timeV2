package com.rivers.nba.entity;

import lombok.Data;
import org.springframework.data.relational.core.mapping.Table;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 裁判信息表
 * </p>
 *
 * @author rivers
 * @since 2026-10-02
 */
@Data
@Table("referee")
public class Referee implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    private Long id;

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

    /**
     * 裁判id
     */
    private Long refereeId;

    /**
     * 裁判名
     */
    private String name;

    /**
     * 号码
     */
    private Integer number;

    /**
     * 职位
     */
    private String position;

    /**
     * 大学
     */
    private String college;

    /**
     * 经验
     */
    private Integer experience;

}
