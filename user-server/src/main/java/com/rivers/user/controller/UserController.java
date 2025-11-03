package com.rivers.user.controller;

import com.rivers.core.vo.ResultVO;
import com.rivers.proto.*;
import com.rivers.user.service.IUserService;
import com.rivers.user.vo.MenuTreeVO;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("user")
public class UserController {

    private final IUserService userService;


    public UserController(IUserService userService) {
        this.userService = userService;
    }

    @PostMapping("saveUser")
    public ResultVO<Void> saveUser(@RequestBody SaveUserReq saveUserReq) {
        return userService.saveUser(saveUserReq);
    }

    @PostMapping("updateUser")
    public ResultVO<Void> updateUser(@RequestBody UpdateUserReq updateUserReq) {
        return userService.updateUser(updateUserReq);
    }

    @PostMapping("getUserPage")
    public ResultVO<UserPageRes> getUserPage(@RequestBody UserPageReq userPageReq) {
        return userService.getUserPage(userPageReq);
    }

    @PostMapping("getUserDetail")
    public ResultVO<UserDetailRes> getUserDetail(@RequestBody UserReq userReq) {
        return userService.getUserDetail(userReq);
    }

    @PostMapping("deleteUser")
    public ResultVO<Void> deleteUser(@RequestBody UserReq userReq) {
        return userService.deleteUser(userReq);
    }

    @PostMapping("ownedMenuTree")
    public ResultVO<List<MenuTreeVO>> ownedMenuTree(@RequestBody OwnedMenuTreeReq ownedMenuTreeReq) {
        return userService.ownedMenuTree(ownedMenuTreeReq);
    }
}
