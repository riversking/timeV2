package com.rivers.user.vo;

import com.google.common.collect.Lists;
import com.rivers.core.tree.TreeNode;
import lombok.Data;

import java.util.List;

@Data
public class MenuTreeVO implements TreeNode<Long, MenuTreeVO> {

    private Long id;

    private Long parentId;

    private String menuName;

    private String menuCode;

    private Integer menuType;

    private String routePath;

    private String icon;

    private String permissionCode;

    private Integer sortOrder;

    private Integer status;

    private String component;

    private List<MenuTreeVO> children = Lists.newArrayList();

    @Override
    public void addChild(MenuTreeVO child) {
        children.add(child);
    }
}
