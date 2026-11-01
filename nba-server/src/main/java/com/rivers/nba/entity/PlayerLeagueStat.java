package com.rivers.nba.entity;

import lombok.Data;
import org.springframework.data.relational.core.mapping.Table;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 球员赛季联盟对比表（同步时预计算落库，球员详情直接读库，不做实时聚合）
 * </p>
 *
 * @author rivers
 * @since 2026-11-01
 */
@Data
@Table("player_season_league_stat")
public class PlayerLeagueStat implements Serializable {

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
     * 指标标识（points/rebounds/assists/steals/blocks）
     */
    private String statKey;

    /**
     * 指标名称
     */
    private String statLabel;

    /**
     * 展示顺序
     */
    private Integer sortNo;

    /**
     * 球员场均
     */
    private Double playerValue;

    /**
     * 联盟平均
     */
    private Double leagueAvg;

    /**
     * 联盟最高
     */
    private Double leagueMax;

    /**
     * 联盟名次
     */
    private Integer rankNo;

    /**
     * 参与排名人数
     */
    private Integer totalPlayers;

}
