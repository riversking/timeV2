package com.rivers.im.service;

import com.rivers.proto.SaveMsgReq;
import reactor.core.publisher.Mono;

public interface IMessageService {

    Mono<Void> saveMessage(SaveMsgReq saveMsgReq);


}
