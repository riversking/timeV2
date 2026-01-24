package com.rivers.user.service;

import com.rivers.core.vo.ResultVO;
import com.rivers.proto.*;
import com.rivers.user.vo.MenuTreeVO;
import reactor.core.publisher.Mono;

import java.util.List;

public interface IMenuService {

    Mono<ResultVO<Void>> saveMenu(SaveMenuReq saveMenuReq);

    Mono<ResultVO<Void>> updateMenu(UpdateMenuReq updateMenuReq);

    Mono<ResultVO<List<MenuTreeVO>>> getMenuTree();

    Mono<ResultVO<MenuDetailRes>> getMenuDetail(MenuDetailReq menuDetailReq);

    Mono<ResultVO<Void>> deleteMenu(DeleteMenuReq deleteMenuReq);

    Mono<ResultVO<Void>> saveRoleMenu(SaveRoleMenuReq saveRoleMenuReq);

    Mono<ResultVO<Void>> removeRoleMenu(RemoveRoleMenuReq removeRoleMenuReq);

    Mono<ResultVO<CheckedMenuRes>> getRoleMenu(CheckedMenuReq checkedMenuReq);
}
