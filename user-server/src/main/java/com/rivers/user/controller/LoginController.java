package com.rivers.user.controller;

import com.rivers.core.vo.ResultVO;
import com.rivers.proto.*;
import com.rivers.user.service.ILoginService;
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
    public ResultVO<AutoLoginRes> login(@RequestBody LoginReq loginReq) {
        return loginService.login(loginReq);
    }

    @PostMapping("autoLogin")
    public ResultVO<AutoLoginRes> autoLogin(@RequestHeader("Authorization") String authHeader) {
        return loginService.autoLogin(authHeader);
    }

    @PostMapping("refresh")
    public ResultVO<AutoLoginRes> refresh(@RequestBody AutoLoginReq autoLoginReq) {
        return loginService.refresh(autoLoginReq);
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
