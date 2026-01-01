package com.rivers.user.controller;

import com.rivers.core.vo.ResultVO;
import com.rivers.proto.LoginReq;
import com.rivers.proto.LoginRes;
import com.rivers.user.service.ILoginService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
public class LoginController {

    private final ILoginService loginService;

    public LoginController(ILoginService loginService) {
        this.loginService = loginService;
    }

    @PostMapping("login")
    public Mono<ResultVO<LoginRes>> login(@RequestBody LoginReq loginReq) {
        return loginService.login(loginReq);
    }
}
