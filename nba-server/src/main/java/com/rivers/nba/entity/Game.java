package com.rivers.nba.entity;

import lombok.Data;
import org.springframework.data.relational.core.mapping.Table;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 比赛信息表
 * </p>
 *
 * @author rivers
 * @since 2026-10-02
 */
@Data
@Table("game")
public class Game implements Serializable {

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
     * 比赛id
     */
    private Integer gameId;

    /**
     * 赛季
     */
    private Integer season;

    /**
     * 赛季类型
     */
    private Integer seasonType;

    /**
     * 状态
     */
    private String status;

    /**
     * 比赛日
     */
    private LocalDateTime day;

    /**
     * 比赛时间
     */
    private LocalDateTime dateTime;

    /**
     * 客队缩写
     */
    private String awayTeam;

    /**
     * 主队缩写
     */
    private String homeTeam;

    /**
     * 客队id
     */
    private Integer awayTeamId;

    /**
     * 主队id
     */
    private Integer homeTeamId;

    /**
     * 球馆id
     */
    private Integer stadiumId;

    /**
     * 转播渠道
     */
    private String channel;

    /**
     * 上座人数
     */
    private Integer attendance;

    /**
     * 客队得分
     */
    private Integer awayTeamScore;

    /**
     * 主队得分
     */
    private Integer homeTeamScore;

    /**
     * 更新时间
     */
    private LocalDateTime updated;

    /**
     * 是否完场
     */
    private Boolean isClosed;

    /**
     * 完场时间
     */
    private LocalDateTime gameEndDateTime;

    /**
     * 是否中立场地
     */
    private Boolean neutralVenue;

    /**
     * 比赛时间UTC
     */
    private LocalDateTime dateTimeUtc;

    /**
     * 是否季中锦标赛
     */
    private Boolean inseasonTournament;

    /**
     * 全局比赛id
     */
    private Integer globalGameId;

}
