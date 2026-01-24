// com.rivers.user.service.IUserService
package com.rivers.user.service;

import com.rivers.core.vo.ResultVO;
import com.rivers.proto.*;
import com.rivers.user.vo.MenuTreeVO;
import reactor.core.publisher.Mono;

import java.util.List;

public interface IUserService {
    Mono<ResultVO<Void>> saveUser(SaveUserReq saveUserReq);

    Mono<ResultVO<Void>> updateUser(UpdateUserReq updateUserReq);

    Mono<ResultVO<UserPageRes>> getUserPage(UserPageReq userPageReq);

    Mono<ResultVO<UserDetailRes>> getUserDetail(UserReq userReq);

    Mono<ResultVO<Void>> deleteUser(UserReq userReq);

    Mono<ResultVO<List<MenuTreeVO>>> ownedMenuTree(OwnedMenuTreeReq ownedMenuTreeReq);

    Mono<ResultVO<UserDetailRes>> getCurrentUser(CommonReq commonReq);

    Mono<ResultVO<Void>> enableUser(EnableUserReq enableUserReq);

    Mono<ResultVO<Void>> disableUser(DisableUserReq disableUserReq);

    Mono<ResultVO<Void>> resetPassword(ResetPasswordReq resetPasswordReq);
}