package com.rivers.nba.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.rivers.core.entity.BasicDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * <p>
 * 球员信息表
 * </p>
 *
 * @author xx
 * @since 2025-10-01
 */
@EqualsAndHashCode(callSuper = true)
@Data
@TableName("player")
public class TimerPlayer extends BasicDO<TimerPlayer> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 球员id
     */
    @TableField("player_id")
    private Integer playerId;

    /**
     * 球队名称
     */
    @TableField("team")
    private String team;

    /**
     * 球队id
     */
    @TableField("team_id")
    private Integer teamId;

    /**
     * 球衣号码
     */
    @TableField("jersey")
    private Integer jersey;

    /**
     * 球员状态
     */
    @TableField("status")
    private String status;

    /**
     * 位置分类
     */
    @TableField("position_category")
    private String positionCategory;

    /**
     * 球员位置
     */
    @TableField("position")
    private String position;

    /**
     * 球员名字
     */
    @TableField("first_name")
    private String firstName;

    /**
     * 球员的姓
     */
    @TableField("last_name")
    private String lastName;

    /**
     * 球员身高 米
     */
    @TableField("height")
    private Integer height;

    /**
     * 球员体重 磅
     */
    @TableField("weight")
    private Integer weight;

    /**
     * 球员出生日期
     */
    @TableField("birth_date")
    private Date birthDate;

    /**
     * 球员出生城市
     */
    @TableField("birth_city")
    private String birthCity;

    /**
     * 球员出生州
     */
    @TableField("birth_state")
    private String birthState;

    /**
     * 球员出生国家
     */
    @TableField("birth_country")
    private String birthCountry;

    /**
     * 大学
     */
    @TableField("college")
    private String college;

    /**
     * 年薪
     */
    @TableField("salary")
    private Integer salary;

    /**
     * 头像
     */
    @TableField("photo_url")
    private String photoUrl;

    /**
     * 职业年限
     */
    @TableField("experience")
    private Integer experience;

    /**
     * 球员全称
     */
    @TableField("draft_kings_name")
    private String draftKingsName;

}
