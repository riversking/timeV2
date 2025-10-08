package com.rivers.user.service;

import com.rivers.core.vo.ResultVO;
import com.rivers.proto.SaveUserReq;
import com.rivers.proto.UpdateUserReq;
import com.rivers.proto.UserPageReq;
import com.rivers.proto.UserPageRes;

public interface IUserService {

    ResultVO<ResultVO.EmptyType> saveUser(SaveUserReq saveUserReq);

    ResultVO<ResultVO.EmptyType> updateUser(UpdateUserReq updateUserReq);

    ResultVO<UserPageRes> getUserPage(UserPageReq userPageReq);
}
