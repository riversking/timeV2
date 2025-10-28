package com.rivers.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.activerecord.AbstractModel;
import com.rivers.core.tree.TreeFactory;
import com.rivers.core.vo.ResultVO;
import com.rivers.proto.*;
import com.rivers.user.entity.TimerMenu;
import com.rivers.user.mapper.TimerMenuMapper;
import com.rivers.user.service.IMenuService;
import com.rivers.user.vo.MenuTreeVO;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

@Service
public class MenuServiceImpl implements IMenuService {

    private final TimerMenuMapper timerMenuMapper;

    public MenuServiceImpl(TimerMenuMapper timerMenuMapper) {
        this.timerMenuMapper = timerMenuMapper;
    }

    @Override
    public ResultVO<Void> saveMenu(SaveMenuReq saveMenuReq) {
        String menuCode = saveMenuReq.getMenuCode();
        String menuName = saveMenuReq.getMenuName();
        if (StringUtils.isBlank(menuCode)) {
            return ResultVO.fail("菜单编码不能为空");
        }
        if (StringUtils.isBlank(menuName)) {
            return ResultVO.fail("菜单名称不能为空");
        }
        LambdaQueryWrapper<TimerMenu> menuWrapper = Wrappers.lambdaQuery();
        menuWrapper.eq(TimerMenu::getMenuCode, menuCode);
        long count = timerMenuMapper.selectCount(menuWrapper);
        if (count > 0) {
            return ResultVO.fail("菜单编码已存在");
        }
        int menuType = saveMenuReq.getMenuType();
        String icon = saveMenuReq.getIcon();
        long parentId = saveMenuReq.getParentId();
        String routePath = saveMenuReq.getRoutePath();
        String permissionCode = saveMenuReq.getPermissionCode();
        int sortOrder = saveMenuReq.getSortOrder();
        LoginUser loginUser = saveMenuReq.getLoginUser();
        String userId = loginUser.getUserId();
        TimerMenu timerMenu = new TimerMenu();
        timerMenu.setMenuCode(menuCode);
        timerMenu.setMenuName(menuName);
        timerMenu.setMenuType(menuType);
        timerMenu.setIcon(icon);
        timerMenu.setParentId(parentId == 0 ? -1L : parentId);
        timerMenu.setRoutePath(routePath);
        timerMenu.setPermissionCode(permissionCode);
        timerMenu.setSortOrder(sortOrder);
        timerMenu.setCreateUser(userId);
        timerMenu.setUpdateUser(userId);
        timerMenuMapper.insert(timerMenu);
        return ResultVO.ok();
    }

    @Override
    public ResultVO<Void> updateMenu(UpdateMenuReq updateMenuReq) {
        String menuCode = updateMenuReq.getMenuCode();
        String menuName = updateMenuReq.getMenuName();
        if (StringUtils.isBlank(menuCode)) {
            return ResultVO.fail("菜单编码不能为空");
        }
        if (StringUtils.isBlank(menuName)) {
            return ResultVO.fail("菜单名称不能为空");
        }
        long id = updateMenuReq.getId();
        LambdaQueryWrapper<TimerMenu> menuWrapper = Wrappers.lambdaQuery();
        menuWrapper.eq(TimerMenu::getMenuCode, menuCode);
        TimerMenu menu = timerMenuMapper.selectOne(menuWrapper);
        if (Objects.nonNull(menu) && !menu.getId().equals(id)) {
            return ResultVO.fail("菜单编码已存在");
        }
        int menuType = updateMenuReq.getMenuType();
        String icon = updateMenuReq.getIcon();
        long parentId = updateMenuReq.getParentId();
        String routePath = updateMenuReq.getRoutePath();
        String permissionCode = updateMenuReq.getPermissionCode();
        int sortOrder = updateMenuReq.getSortOrder();
        LoginUser loginUser = updateMenuReq.getLoginUser();
        String userId = loginUser.getUserId();
        TimerMenu timerMenu = new TimerMenu();
        timerMenu.setId(id);
        timerMenu.setMenuCode(menuCode);
        timerMenu.setMenuName(menuName);
        timerMenu.setMenuType(menuType);
        timerMenu.setIcon(icon);
        timerMenu.setParentId(parentId);
        timerMenu.setRoutePath(routePath);
        timerMenu.setPermissionCode(permissionCode);
        timerMenu.setSortOrder(sortOrder);
        timerMenu.setUpdateUser(userId);
        timerMenu.updateById();
        return ResultVO.ok();
    }

    @Override
    public ResultVO<List<MenuTreeVO>> getMenuTree() {
        LambdaQueryWrapper<TimerMenu> menuWrapper = Wrappers.lambdaQuery();
        List<TimerMenu> timerMenus = timerMenuMapper.selectList(menuWrapper);
        List<MenuTreeVO> list = timerMenus.stream()
                .map(i -> {
                    MenuTreeVO menuTreeVO = new MenuTreeVO();
                    BeanUtils.copyProperties(i, menuTreeVO);
                    return menuTreeVO;
                })
                .toList();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            // 使用虚拟线程并行处理节点转换
            List<CompletableFuture<MenuTreeVO>> futures = list
                    .parallelStream()
                    .map(i -> CompletableFuture.supplyAsync(
                            () -> i, executor))
                    .toList();
            // 收集转换结果
            List<MenuTreeVO> trees = futures.stream()
                    .map(CompletableFuture::join)
                    .toList();
            // 构建树结构
            TreeFactory<Long, MenuTreeVO> treeFactory = new TreeFactory<>();
            List<MenuTreeVO> menuTree = treeFactory.buildTree(trees);
            return ResultVO.ok(menuTree);
        }
    }

    @Override
    public ResultVO<MenuDetailRes> getMenuDetail(MenuDetailReq menuDetailReq) {
        String menuCode = menuDetailReq.getMenuCode();
        if (StringUtils.isBlank(menuCode)) {
            return ResultVO.fail("菜单编码不能为空");
        }
        LambdaQueryWrapper<TimerMenu> menuWrapper = Wrappers.lambdaQuery();
        menuWrapper.eq(TimerMenu::getMenuCode, menuCode);
        TimerMenu menu = timerMenuMapper.selectOne(menuWrapper);
        MenuDetailRes menuDetailRes = Optional.ofNullable(menu)
                .map(i ->
                        MenuDetailRes.newBuilder()
                                .setId(i.getId())
                                .setMenuCode(i.getMenuCode())
                                .setMenuName(i.getMenuName())
                                .setMenuType(i.getMenuType())
                                .setIcon(i.getIcon())
                                .setParentId(i.getParentId())
                                .setRoutePath(i.getRoutePath())
                                .setSortOrder(i.getSortOrder())
                                .setPermissionCode(i.getPermissionCode())
                                .build())
                .orElse(null);
        return ResultVO.ok(menuDetailRes);
    }

    @Override
    public ResultVO<Void> deleteMenu(DeleteMenuReq deleteMenuReq) {
        String menuCode = deleteMenuReq.getMenuCode();
        if (StringUtils.isBlank(menuCode)) {
            return ResultVO.fail("菜单编码不能为空");
        }
        LambdaQueryWrapper<TimerMenu> menuWrapper = Wrappers.lambdaQuery();
        menuWrapper.eq(TimerMenu::getMenuCode, menuCode);
        Optional.ofNullable(timerMenuMapper.selectOne(menuWrapper))
                .ifPresent(AbstractModel::deleteById);
        return ResultVO.ok();
    }
}
