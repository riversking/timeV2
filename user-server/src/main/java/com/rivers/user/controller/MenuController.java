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
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.SequencedCollection;

@RestController
@RequestMapping("menu")
public class MenuController {

    private final IMenuService menuService;

    public MenuController(IMenuService menuService) {
        this.menuService = menuService;
    }

    @PostMapping("saveMenu")
    public Mono<ResultVO<Void>> saveMenu(@RequestBody SaveMenuReq req) {
        return menuService.saveMenu(req);
    }

    @PostMapping("updateMenu")
    public Mono<ResultVO<Void>> updateMenu(@RequestBody UpdateMenuReq req) {
        return menuService.updateMenu(req);
    }

    @PostMapping("getMenuTree")
    public Mono<ResultVO<List<MenuTreeVO>>> getMenuTree() {
        return menuService.getMenuTree();
    }

    @PostMapping("getMenuDetail")
    public Mono<ResultVO<MenuDetailRes>> getMenuDetail(@RequestBody MenuDetailReq menuDetailReq) {
        return menuService.getMenuDetail(menuDetailReq);
    }

    @PostMapping("deleteMenu")
    public Mono<ResultVO<Void>> deleteMenu(@RequestBody DeleteMenuReq deleteMenuReq) {
        return menuService.deleteMenu(deleteMenuReq);
    }

    @PostMapping("saveRoleMenu")
    public Mono<ResultVO<Void>> saveRoleMenu(@RequestBody SaveRoleMenuReq saveRoleMenuReq) {
        return menuService.saveRoleMenu(saveRoleMenuReq);
    }

    @PostMapping("removeRoleMenu")
    public Mono<ResultVO<Void>> removeRoleMenu(@RequestBody RemoveRoleMenuReq removeRoleMenuReq) {
        return menuService.removeRoleMenu(removeRoleMenuReq);
    }

    @PostMapping("getRoleMenuTree")
    public Mono<ResultVO<SequencedCollection<RoleMenuTreeVO>>> getRoleMenuTree(@RequestBody RoleMenuTreeReq roleMenuTreeReq) {
        return menuService.getRoleMenuTree(roleMenuTreeReq);
    }
}