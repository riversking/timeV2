package com.rivers.user.service;

import com.rivers.core.vo.ResultVO;
import com.rivers.proto.LoginReq;
import com.rivers.proto.LoginRes;
import reactor.core.publisher.Mono;

public interface ILoginService {

    Mono<ResultVO<LoginRes>> login(LoginReq loginReq);
}
