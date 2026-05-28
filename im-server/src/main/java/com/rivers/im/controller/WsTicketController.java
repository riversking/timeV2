package com.rivers.im.controller;

import com.rivers.core.vo.ResultVO;
import com.rivers.im.service.IWsTicketService;
import com.rivers.proto.CreateTicketReq;
import com.rivers.proto.CreateTicketRes;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
@RequestMapping("wsTicket")
public class WsTicketController {

    private final IWsTicketService wsTicketService;

    @PostMapping("createTicket")
    public Mono<ResultVO<CreateTicketRes>> createTicket(CreateTicketReq createTicketReq) {
        return wsTicketService.createTicket(createTicketReq);
    }
}
