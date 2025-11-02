package com.rivers.user.vo;

import com.google.common.collect.Lists;
import com.rivers.core.tree.TreeNode;
import lombok.Data;

import java.util.List;

@Data
public class RoleMenuTreeVO implements TreeNode<Long, RoleMenuTreeVO> {

    private Long id;

    private Long parentId;

    private String menuName;

    private String menuCode;

    private Integer sortOrder;

    private boolean checked;

    private List<RoleMenuTreeVO> children = Lists.newArrayList();

    @Override
    public void addChild(RoleMenuTreeVO child) {
        children.add(child);
    }
}
