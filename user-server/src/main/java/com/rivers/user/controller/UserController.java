package com.rivers.user.controller;

import com.rivers.core.vo.ResultVO;
import com.rivers.proto.SaveUserReq;
import com.rivers.proto.UpdateUserReq;
import com.rivers.proto.UserPageReq;
import com.rivers.proto.UserPageRes;
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
}
