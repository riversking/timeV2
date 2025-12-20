package com.rivers.nba.client;

import com.rivers.core.vo.ResultVO;
import com.rivers.proto.DicDataReq;
import com.rivers.proto.DicDataRes;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import reactor.core.publisher.Mono;


@HttpExchange(url = "http://user-server")
public interface UserServiceClient {

    @PostExchange("/dic/getDicData")
    Mono<ResultVO<DicDataRes>> getDicData(@RequestBody DicDataReq dicDataReq);

}
