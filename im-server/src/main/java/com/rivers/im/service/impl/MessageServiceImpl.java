package com.rivers.im.service.impl;

import com.rivers.im.mapper.TimerMessageMapper;
import com.rivers.im.service.IMessageService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class MessageServiceImpl implements IMessageService {

    private final TimerMessageMapper timerMessageMapper;

    public MessageServiceImpl(TimerMessageMapper timerMessageMapper) {
        this.timerMessageMapper = timerMessageMapper;
    }

    @Override
    public Mono<Void> saveMessage() {
        return null;
    }
}
