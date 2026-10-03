package com.rivers.nba.entity;

import lombok.Data;
import org.springframework.data.relational.core.mapping.Table;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 球员信息表
 * </p>
 *
 * @author xx
 * @since 2025-10-01
 */
@Data
@Table("player")
public class TimerPlayer implements Serializable {

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
    private Integer playerId;

    /**
     * 球队名称
     */
    private String team;

    /**
     * 球队id
     */
    private Integer teamId;

    /**
     * 球衣号码
     */
    private Integer jersey;

    /**
     * 球员状态
     */
    private String status;

    /**
     * 位置分类
     */
    private String positionCategory;

    /**
     * 球员位置
     */
    private String position;

    /**
     * 球员名字
     */
    private String firstName;

    /**
     * 球员的姓
     */
    private String lastName;

    /**
     * 球员身高 米
     */
    private Integer height;

    /**
     * 球员体重 磅
     */
    private Integer weight;

    /**
     * 球员出生日期
     */
    private LocalDateTime birthDate;

    /**
     * 球员出生城市
     */
    private String birthCity;

    /**
     * 球员出生州
     */
    private String birthState;

    /**
     * 球员出生国家
     */
    private String birthCountry;

    /**
     * 大学
     */
    private String college;

    /**
     * 年薪
     */
    private Integer salary;

    /**
     * 头像
     */
    private String photoUrl;

    /**
     * 职业年限
     */
    private Integer experience;

    /**
     * 球员全称
     */
    private String draftKingsName;

}
