package com.rivers.nba.entity;

import lombok.Data;
import org.springframework.data.relational.core.mapping.Table;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 球员历年数据表：按赛季 + 赛程分类（季前赛/常规赛/季后赛）存全量统计，
 * 详情页直接读表展示，不在请求链路聚合或访问上游
 * </p>
 *
 * @author rivers
 * @since 2026-11-01
 */
@Data
@Table("player_season_history")
public class PlayerSeasonHistory implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private String createUser;

    private LocalDateTime createTime;

    private String updateUser;

    private LocalDateTime updateTime;

    private Integer isDeleted;

    /**
     * 赛季（结束年份）
     */
    private Integer season;

    /**
     * 赛程分类：1 季前赛 2 常规赛 3 季后赛
     */
    private Integer seasonType;

    /**
     * 球员id（库内体系）
     */
    private Long playerId;

    /**
     * 球员姓名
     */
    private String playerName;

    /**
     * 球队缩写
     */
    private String team;

    /**
     * 出场场次
     */
    private Integer games;

    /**
     * 总出场分钟
     */
    private Double minutes;

    /**
     * 总得分
     */
    private Double points;

    /**
     * 投篮命中总数
     */
    private Double fieldGoalsMade;

    /**
     * 投篮出手总数
     */
    private Double fieldGoalsAttempted;

    /**
     * 三分命中总数
     */
    private Double threePointersMade;

    /**
     * 三分出手总数
     */
    private Double threePointersAttempted;

    /**
     * 罚球命中总数
     */
    private Double freeThrowsMade;

    /**
     * 罚球出手总数
     */
    private Double freeThrowsAttempted;

    private Double rebounds;

    private Double assists;

    private Double steals;

    private Double blockedShots;

    private Double fouls;
}
