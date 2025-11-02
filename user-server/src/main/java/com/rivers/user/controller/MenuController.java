package com.rivers.user.controller;

import com.rivers.core.vo.ResultVO;
import com.rivers.proto.*;
import com.rivers.user.service.IMenuService;
import com.rivers.user.vo.MenuTreeVO;
import com.rivers.user.vo.RoleMenuTreeVO;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("menu")
public class MenuController {

    private final IMenuService menuService;

    public MenuController(IMenuService menuService) {
        this.menuService = menuService;
    }

    @PostMapping("saveMenu")
    public ResultVO<Void> saveMenu(@RequestBody SaveMenuReq saveMenuReq) {
        return menuService.saveMenu(saveMenuReq);
    }

    @PostMapping("updateMenu")
    public ResultVO<Void> updateMenu(@RequestBody UpdateMenuReq updateMenuReq) {
        return menuService.updateMenu(updateMenuReq);
    }

    @PostMapping("getMenuTree")
    public ResultVO<List<MenuTreeVO>> getMenuTree() {
        return menuService.getMenuTree();
    }

    @PostMapping("getMenuDetail")
    public ResultVO<MenuDetailRes> getMenuDetail(@RequestBody MenuDetailReq menuDetailReq) {
        return menuService.getMenuDetail(menuDetailReq);
    }

    @PostMapping("deleteMenu")
    public ResultVO<Void> deleteMenu(@RequestBody DeleteMenuReq deleteMenuReq) {
        return menuService.deleteMenu(deleteMenuReq);
    }

    @PostMapping("saveRoleMenu")
    public ResultVO<Void> saveRoleMenu(@RequestBody SaveRoleMenuReq saveRoleMenuReq) {
        return menuService.saveRoleMenu(saveRoleMenuReq);
    }

    @PostMapping("removeRoleMenu")
    public ResultVO<Void> removeRoleMenu(@RequestBody RemoveRoleMenuReq removeRoleMenuReq) {
        return menuService.removeRoleMenu(removeRoleMenuReq);
    }

    @PostMapping("getRoleMenuTree")
    public ResultVO<List<RoleMenuTreeVO>> getRoleMenuTree(@RequestBody RoleMenuTreeReq roleMenuTreeReq) {
        return menuService.getRoleMenuTree(roleMenuTreeReq);
    }
}
