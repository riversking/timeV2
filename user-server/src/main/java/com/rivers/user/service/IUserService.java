package com.rivers.user.service;

import com.rivers.core.vo.ResultVO;
import com.rivers.proto.*;

public interface IUserService {

    ResultVO<ResultVO.EmptyType> saveUser(SaveUserReq saveUserReq);

    ResultVO<ResultVO.EmptyType> updateUser(UpdateUserReq updateUserReq);

    ResultVO<UserPageRes> getUserPage(UserPageReq userPageReq);

    ResultVO<UserDetailRes> getUserDetail(UserReq userReq);

    ResultVO<ResultVO.EmptyType> deleteUser(UserReq userReq);
}
