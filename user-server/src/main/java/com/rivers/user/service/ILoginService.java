package com.rivers.user.service;

import com.rivers.core.vo.ResultVO;
import com.rivers.proto.LoginReq;
import com.rivers.proto.LoginRes;

public interface ILoginService {

    ResultVO<LoginRes> login(LoginReq loginReq);
}
