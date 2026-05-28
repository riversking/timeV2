package com.rivers.im.service;

import com.rivers.core.vo.ResultVO;
import com.rivers.proto.CreateTicketReq;
import com.rivers.proto.CreateTicketRes;
import reactor.core.publisher.Mono;

public interface IWsTicketService {

    Mono<ResultVO<CreateTicketRes>> createTicket(CreateTicketReq createTicketReq);

    Mono<String> consumeTicket(String ticket);
}
