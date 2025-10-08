package com.rivers.user.controller;

import com.rivers.core.vo.ResultVO;
import com.rivers.proto.*;
import com.rivers.user.service.IUserService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("user")
public class UserController {

    private final IUserService userService;


    public UserController(IUserService userService) {
        this.userService = userService;
    }

    @PostMapping("saveUser")
    public ResultVO<ResultVO.EmptyType> saveUser(@RequestBody SaveUserReq saveUserReq) {
        return userService.saveUser(saveUserReq);
    }

    @PostMapping("updateUser")
    public ResultVO<ResultVO.EmptyType> updateUser(@RequestBody UpdateUserReq updateUserReq) {
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
    public ResultVO<ResultVO.EmptyType> deleteUser(@RequestBody UserReq userReq) {
        return userService.deleteUser(userReq);
    }
}
