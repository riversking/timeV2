package com.rivers.nba.entity;

import lombok.Data;
import org.springframework.data.relational.core.mapping.Table;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 球馆信息表
 * </p>
 *
 * @author rivers
 * @since 2026-10-02
 */
@Data
@Table("stadium")
public class TimerStadium implements Serializable {

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
     * 球馆id
     */
    private Integer stadiumId;

    /**
     * 是否使用中
     */
    private Boolean active;

    /**
     * 球馆名称
     */
    private String name;

    /**
     * 地址
     */
    private String address;

    /**
     * 城市
     */
    private String city;

    /**
     * 州
     */
    private String state;

    /**
     * 邮编
     */
    private String zip;

    /**
     * 国家
     */
    private String country;

    /**
     * 容量
     */
    private Integer capacity;

    /**
     * 纬度
     */
    private Double geoLat;

    /**
     * 经度
     */
    private Double geoLong;

}
