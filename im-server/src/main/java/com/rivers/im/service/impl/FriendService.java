package com.rivers.im.service.impl;

import com.rivers.im.mapper.TimerFriendMapper;
import com.rivers.im.service.IFriendService;
import org.springframework.stereotype.Service;

@Service
public class FriendService implements IFriendService {

    private final TimerFriendMapper timerFriendMapper;

    public FriendService(TimerFriendMapper timerFriendMapper) {
        this.timerFriendMapper = timerFriendMapper;
    }
}
