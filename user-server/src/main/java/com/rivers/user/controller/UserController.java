package com.rivers.user.controller;

import com.rivers.core.vo.ResultVO;
import com.rivers.proto.*;
import com.rivers.user.service.IUserService;
import com.rivers.user.vo.MenuTreeVO;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("user")
public class UserController {

    private final IUserService userService;

    public UserController(IUserService userService) {
        this.userService = userService;
    }

    @PostMapping("saveUser")
    public Mono<ResultVO<Void>> saveUser(@RequestBody SaveUserReq saveUserReq) {
        return userService.saveUser(saveUserReq);
    }

    @PostMapping("updateUser")
    public Mono<ResultVO<Void>> updateUser(@RequestBody UpdateUserReq updateUserReq) {
        return userService.updateUser(updateUserReq);
    }

    @PostMapping("getUserPage")
    public Mono<ResultVO<UserPageRes>> getUserPage(@RequestBody UserPageReq userPageReq) {
        return userService.getUserPage(userPageReq);
    }

    @PostMapping("getUserDetail")
    public Mono<ResultVO<UserDetailRes>> getUserDetail(@RequestBody UserReq userReq) {
        return userService.getUserDetail(userReq);
    }

    @PostMapping("deleteUser")
    public Mono<ResultVO<Void>> deleteUser(@RequestBody UserReq userReq) {
        return userService.deleteUser(userReq);
    }

    @PostMapping("ownedMenuTree")
    public Mono<ResultVO<List<MenuTreeVO>>> ownedMenuTree(@RequestBody OwnedMenuTreeReq ownedMenuTreeReq) {
        return userService.ownedMenuTree(ownedMenuTreeReq);
    }

    @PostMapping("getCurrentUser")
    public Mono<ResultVO<UserDetailRes>> getCurrentUser(@RequestBody CommonReq commonReq) {
        return userService.getCurrentUser(commonReq);
    }

    @PostMapping("enableUser")
    public Mono<ResultVO<Void>> enableUser(@RequestBody EnableUserReq enableUserReq) {
        return userService.enableUser(enableUserReq);
    }

    @PostMapping("disableUser")
    public Mono<ResultVO<Void>> disableUser(@RequestBody DisableUserReq disableUserReq) {
        return userService.disableUser(disableUserReq);
    }

    @PostMapping("resetPassword")
    public Mono<ResultVO<Void>> resetPassword(@RequestBody ResetPasswordReq resetPasswordReq) {
        return userService.resetPassword(resetPasswordReq);
    }
}