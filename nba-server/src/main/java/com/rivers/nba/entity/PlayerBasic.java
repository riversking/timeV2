package com.rivers.nba.entity;

import lombok.Data;
import org.springframework.data.relational.core.mapping.Table;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 球员简档信息表
 * </p>
 *
 * @author rivers
 * @since 2026-10-02
 */
@Data
@Table("player_basic")
public class PlayerBasic implements Serializable {

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
     * 球员id
     */
    private Long playerId;

    /**
     * sportsdata id
     */
    private String sportsDataId;

    /**
     * 状态
     */
    private String status;

    /**
     * 球队id
     */
    private Integer teamId;

    /**
     * 球队缩写
     */
    private String team;

    /**
     * 球衣号
     */
    private Integer jersey;

    /**
     * 位置大类
     */
    private String positionCategory;

    /**
     * 位置
     */
    private String position;

    /**
     * 名
     */
    private String firstName;

    /**
     * 姓
     */
    private String lastName;

    /**
     * 身高(英寸)
     */
    private Integer height;

    /**
     * 体重(磅)
     */
    private Integer weight;

    /**
     * 出生日期
     */
    private LocalDateTime birthDate;

    /**
     * 出生城市
     */
    private String birthCity;

    /**
     * 出生州
     */
    private String birthState;

    /**
     * 出生国家
     */
    private String birthCountry;

    /**
     * 大学
     */
    private String college;

    /**
     * 薪水
     */
    private Integer salary;

    /**
     * 头像地址
     */
    private String photoUrl;

    /**
     * 球龄
     */
    private Integer experience;

    /**
     * 全名
     */
    private String draftKingsName;

    /**
     * 全局球队id
     */
    private Integer globalTeamId;

    /**
     * 来源1现役2自由简档3球队简档4自由全量
     */
    private Integer source;

}
