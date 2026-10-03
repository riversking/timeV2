package com.rivers.nba.entity;

import lombok.Data;
import org.springframework.data.relational.core.mapping.Table;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 阵容深度表
 * </p>
 *
 * @author rivers
 * @since 2026-10-02
 */
@Data
@Table("depth_chart")
public class DepthChart implements Serializable {

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
     * 球员id
     */
    private Long playerId;

    /**
     * 球员名
     */
    private String name;

    /**
     * 位置大类
     */
    private String positionCategory;

    /**
     * 位置
     */
    private String position;

    /**
     * 深度顺序
     */
    private Integer depthOrder;

    /**
     * 阵容深度id
     */
    private Integer depthChartId;

    /**
     * 更新时间
     */
    private LocalDateTime updated;

}
