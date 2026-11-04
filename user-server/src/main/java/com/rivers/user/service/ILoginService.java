package com.rivers.user.service;

import com.rivers.core.vo.ResultVO;
import com.rivers.proto.*;
import reactor.core.publisher.Mono;

/**
 * @author xx
 */
public interface ILoginService {

    Mono<ResultVO<AutoLoginRes>> login(LoginReq loginReq);

    Mono<ResultVO<AutoLoginRes>> autoLogin(String authHeader);

    Mono<ResultVO<QrCodeRes>> generateQrCode();

    Mono<ResultVO<Void>> scanQrCode(ScanQrCodeReq scanQrCodeReq);

    Mono<ResultVO<Void>> confirmQrCode(ConfirmQrCodeReq confirmQrCodeReq);

}
