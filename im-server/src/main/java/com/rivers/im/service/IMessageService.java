package com.rivers.im.service;

import reactor.core.publisher.Mono;

public interface IMessageService {

    Mono<Void> saveMessage();


}
