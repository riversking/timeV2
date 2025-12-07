package com.rivers.user.vo;

import com.google.common.collect.Lists;
import com.rivers.core.tree.TreeNode;
import lombok.Data;

import java.util.List;

@Data
public class DicTreeVO implements TreeNode<Long, DicTreeVO> {

    private Long id;

    private String dicKey;

    /**
     * 字典值
     */
    private String dicValue;

    /**
     * 字典描述
     */
    private String dicDesc;

    /**
     * 排序
     */
    private Integer sort;

    /**
     * 父类id
     */
    private Long parentId;

    private List<DicTreeVO> children = Lists.newArrayList();

    @Override
    public void addChild(DicTreeVO child) {
        children.add(child);
    }
}
