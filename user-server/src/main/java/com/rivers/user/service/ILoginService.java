package com.rivers.user.service;

import com.rivers.core.vo.ResultVO;
import com.rivers.proto.AutoLoginReq;
import com.rivers.proto.AutoLoginRes;
import com.rivers.proto.LoginReq;

/**
 * @author xx
 */
public interface ILoginService {

    ResultVO<AutoLoginRes> login(LoginReq loginReq);

    ResultVO<AutoLoginRes> autoLogin(String authHeader);

    ResultVO<AutoLoginRes> refresh(AutoLoginReq autoLoginReq);
}