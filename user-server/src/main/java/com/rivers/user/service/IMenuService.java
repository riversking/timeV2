package com.rivers.user.service;

import com.rivers.core.vo.ResultVO;
import com.rivers.proto.*;
import com.rivers.user.vo.MenuTreeVO;

import java.util.List;

public interface IMenuService {

    ResultVO<Void> saveMenu(SaveMenuReq saveMenuReq);

    ResultVO<Void> updateMenu(UpdateMenuReq updateMenuReq);

    ResultVO<List<MenuTreeVO>> getMenuTree();

    ResultVO<MenuDetailRes> getMenuDetail(MenuDetailReq menuDetailReq);

    ResultVO<Void> deleteMenu(DeleteMenuReq deleteMenuReq);
}
