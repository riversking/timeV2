package com.rivers.nba.entity;

import lombok.Data;
import org.springframework.data.relational.core.mapping.Table;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 球员赛季统计表（存全量统计，榜单读取时按统计类型排序取前 N）
 * </p>
 *
 * @author rivers
 * @since 2026-10-16
 */
@Data
@Table("player_season_stats")
public class PlayerSeasonStat implements Serializable {

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
     * 球员id
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
     * 位置
     */
    private String position;

    /**
     * 出场场次
     */
    private Integer games;

    /**
     * 总得分
     */
    private Double points;

    /**
     * 总篮板
     */
    private Double rebounds;

    /**
     * 总助攻
     */
    private Double assists;

    /**
     * 总抢断
     */
    private Double steals;

    /**
     * 总盖帽
     */
    private Double blockedShots;

    /**
     * 三分命中总数
     */
    private Double threePointersMade;

    /**
     * 投篮命中总数
     */
    private Double fieldGoalsMade;

    /**
     * 投篮出手总数
     */
    private Double fieldGoalsAttempted;

}
