package com.rivers.user.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.rivers.core.entity.BasicDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * <p>
 *
 * </p>
 *
 * @author xx
 * @since 2025-12-07
 */
@EqualsAndHashCode(callSuper = true)
@Data
@TableName("timer_dic")
public class TimerDic extends BasicDO<TimerDic> {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 字典key
     */
    @TableField("dic_key")
    private String dicKey;

    /**
     * 字典值
     */
    @TableField("dic_value")
    private String dicValue;

    /**
     * 字典描述
     */
    @TableField("dic_desc")
    private String dicDesc;

    /**
     * 排序
     */
    @TableField("sort")
    private Integer sort;

    /**
     * 父类id
     */
    @TableField("parent_id")
    private Long parentId;

}
