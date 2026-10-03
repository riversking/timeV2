package com.rivers.nba.entity;

import lombok.Data;
import org.springframework.data.relational.core.mapping.Table;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 当前赛季信息表
 * </p>
 *
 * @author rivers
 * @since 2026-10-02
 */
@Data
@Table("current_season")
public class CurrentSeason implements Serializable {

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
     * 赛季
     */
    private Integer season;

    /**
     * 开始年份
     */
    private Integer startYear;

    /**
     * 结束年份
     */
    private Integer endYear;

    /**
     * 描述
     */
    private String description;

    /**
     * 常规赛开始时间
     */
    private LocalDateTime regularSeasonStartDate;

    /**
     * 季后赛开始时间
     */
    private LocalDateTime postSeasonStartDate;

    /**
     * 赛季类型
     */
    private String seasonType;

    /**
     * api赛季
     */
    private String apiSeason;

}
