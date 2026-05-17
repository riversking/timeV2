package com.rivers.user.service;

import com.rivers.core.vo.ResultVO;
import com.rivers.proto.*;

/**
 * @author xx
 */
public interface ILoginService {

    ResultVO<AutoLoginRes> login(LoginReq loginReq);

    ResultVO<AutoLoginRes> autoLogin(String authHeader);

    ResultVO<AutoLoginRes> refresh(AutoLoginReq autoLoginReq);

    ResultVO<QrCodeRes> generateQrCode();

    ResultVO<Void> scanQrCode(ScanQrCodeReq scanQrCodeReq);

    ResultVO<Void> confirmQrCode(ConfirmQrCodeReq confirmQrCodeReq);

}