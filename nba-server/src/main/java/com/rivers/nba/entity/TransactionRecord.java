package com.rivers.nba.entity;

import lombok.Data;
import org.springframework.data.relational.core.mapping.Table;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 交易记录表
 * </p>
 *
 * @author rivers
 * @since 2026-10-02
 */
@Data
@Table("transaction_record")
public class TransactionRecord implements Serializable {

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
     * 球员名
     */
    private String name;

    /**
     * 前球队id
     */
    private Integer formerTeamId;

    /**
     * 前球队缩写
     */
    private String formerTeam;

    /**
     * 现球队id
     */
    private Integer teamId;

    /**
     * 现球队缩写
     */
    private String team;

    /**
     * 交易类型
     */
    private String type;

    /**
     * 交易日期
     */
    private LocalDateTime date;

    /**
     * 说明
     */
    private String note;

    /**
     * 创建时间
     */
    private LocalDateTime created;

    /**
     * 更新时间
     */
    private LocalDateTime updated;

}
