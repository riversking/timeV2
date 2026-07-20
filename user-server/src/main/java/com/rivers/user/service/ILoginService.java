package com.rivers.user.service;

import com.rivers.core.vo.ResultVO;
import com.rivers.proto.*;
import org.springframework.http.server.reactive.ServerHttpResponse;

/**
 * @author xx
 */
public interface ILoginService {

    ResultVO<Void> login(LoginReq loginReq, ServerHttpResponse response);

    ResultVO<Void> autoLogin(String authHeader, ServerHttpResponse response);

    ResultVO<QrCodeRes> generateQrCode();

    ResultVO<Void> scanQrCode(ScanQrCodeReq scanQrCodeReq);

    ResultVO<Void> confirmQrCode(ConfirmQrCodeReq confirmQrCodeReq);

}