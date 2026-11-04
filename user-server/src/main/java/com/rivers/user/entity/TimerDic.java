package com.rivers.user.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 字典表（R2DBC 映射）
 *
 * @author xx
 * @since 2025-12-07
 */
@Data
@Table("timer_dic")
public class TimerDic implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    private Long id;

    /**
     * 字典key
     */
    private String dicKey = "";

    /**
     * 字典值
     */
    private String dicValue = "";

    /**
     * 字典描述
     */
    private String dicDesc = "";

    /**
     * 排序
     */
    private Integer sort = 0;

    /**
     * 父类id
     */
    private Long parentId = -1L;

    private String createUser = "";

    private LocalDateTime createTime = LocalDateTime.now();

    private String updateUser = "";

    private LocalDateTime updateTime = LocalDateTime.now();

    private Integer isDeleted = 0;
}
