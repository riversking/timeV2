package com.rivers.nba.entity;

import lombok.Data;
import org.springframework.data.relational.core.mapping.Table;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 球队信息表
 * </p>
 *
 * @author rivers
 * @since 2026-10-02
 */
@Data
@Table("team")
public class Team implements Serializable {

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
     * 球队id
     */
    private Integer teamId;

    /**
     * 球队缩写
     */
    private String key;

    /**
     * 是否现役
     */
    private Boolean active;

    /**
     * 城市
     */
    private String city;

    /**
     * 球队名
     */
    private String name;

    /**
     * 联盟id
     */
    private Integer leagueId;

    /**
     * 球馆id
     */
    private Integer stadiumId;

    /**
     * 分区
     */
    private String conference;

    /**
     * 赛区
     */
    private String division;

    /**
     * 主色
     */
    private String primaryColor;

    /**
     * 次色
     */
    private String secondaryColor;

    /**
     * 第三色
     */
    private String tertiaryColor;

    /**
     * 第四色
     */
    private String quaternaryColor;

    /**
     * logo地址
     */
    private String wikipediaLogoUrl;

    /**
     * 全局球队id
     */
    private Integer globalTeamId;

    /**
     * nba.com球队id
     */
    private Integer nbaDotComTeamId;

    /**
     * 主教练
     */
    private String headCoach;

}
