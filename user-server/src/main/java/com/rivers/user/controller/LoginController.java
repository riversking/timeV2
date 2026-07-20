package com.rivers.user.controller;

import com.rivers.core.vo.ResultVO;
import com.rivers.proto.*;
import com.rivers.user.service.ILoginService;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author xx
 */
@RestController
public class LoginController {

    private final ILoginService loginService;

    public LoginController(ILoginService loginService) {
        this.loginService = loginService;
    }

    @PostMapping("login")
    public ResultVO<Void> login(@RequestBody LoginReq loginReq, ServerHttpResponse response) {
        return loginService.login(loginReq, response);
    }

    @PostMapping("autoLogin")
    public ResultVO<Void> autoLogin(@RequestHeader("Authorization") String authHeader, ServerHttpResponse response) {
        return loginService.autoLogin(authHeader, response);
    }

    @PostMapping("/qrcode/generate")
    public ResultVO<QrCodeRes> generateQrCode() {
        return loginService.generateQrCode();
    }

    // ✅ HTTP POST - 扫描二维码（移动端调用）
    @PostMapping("/qrcode/scan")
    public ResultVO<Void> scanQrCode(@RequestBody ScanQrCodeReq scanQrCodeReq) {
        return loginService.scanQrCode(scanQrCodeReq);
    }

    // ✅ HTTP POST - 确认登录（移动端调用）
    @PostMapping("/qrcode/confirm")
    public ResultVO<Void> confirmQrCode(@RequestBody ConfirmQrCodeReq confirmQrCodeReq) {
        return loginService.confirmQrCode(confirmQrCodeReq);
    }
}
