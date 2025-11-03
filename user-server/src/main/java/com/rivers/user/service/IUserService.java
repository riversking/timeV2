package com.rivers.user.service;

import com.rivers.core.vo.ResultVO;
import com.rivers.proto.*;
import com.rivers.user.vo.MenuTreeVO;

import java.util.List;

public interface IUserService {

    ResultVO<Void> saveUser(SaveUserReq saveUserReq);

    ResultVO<Void> updateUser(UpdateUserReq updateUserReq);

    ResultVO<UserPageRes> getUserPage(UserPageReq userPageReq);

    ResultVO<UserDetailRes> getUserDetail(UserReq userReq);

    ResultVO<Void> deleteUser(UserReq userReq);

    ResultVO<List<MenuTreeVO>> ownedMenuTree(OwnedMenuTreeReq ownedMenuTreeReq);
}
