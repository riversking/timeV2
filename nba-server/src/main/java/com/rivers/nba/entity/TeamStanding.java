package com.rivers.nba.entity;

import lombok.Data;
import org.springframework.data.relational.core.mapping.Table;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 球队赛季排名表
 * </p>
 *
 * @author rivers
 * @since 2026-10-16
 */
@Data
@Table("team_standings")
public class TeamStanding implements Serializable {

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
     * 赛季（结束年份）
     */
    private Integer season;

    /**
     * 球队id
     */
    private Integer teamId;

    /**
     * 球队缩写
     */
    private String teamKey;

    /**
     * 城市
     */
    private String city;

    /**
     * 队名
     */
    private String name;

    /**
     * 联盟
     */
    private String conference;

    /**
     * 分区
     */
    private String division;

    /**
     * 胜场
     */
    private Integer wins;

    /**
     * 负场
     */
    private Integer losses;

    /**
     * 胜差
     */
    private Double gamesBack;

    /**
     * 近10场胜
     */
    private Integer lastTenWins;

    /**
     * 近10场负
     */
    private Integer lastTenLosses;

    /**
     * 连续记录
     */
    private String streakDescription;

    /**
     * 场均得分
     */
    private Double pointsPerGameFor;

    /**
     * 场均失分
     */
    private Double pointsPerGameAgainst;

}
