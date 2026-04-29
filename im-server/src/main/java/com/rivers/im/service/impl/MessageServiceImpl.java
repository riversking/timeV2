package com.rivers.im.service.impl;

import com.rivers.im.entity.TimerMessage;
import com.rivers.im.mapper.TimerMessageMapper;
import com.rivers.im.service.IMessageService;
import com.rivers.proto.LoginUser;
import com.rivers.proto.SaveMsgReq;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Service
public class MessageServiceImpl implements IMessageService {

    private final TimerMessageMapper timerMessageMapper;

    public MessageServiceImpl(TimerMessageMapper timerMessageMapper) {
        this.timerMessageMapper = timerMessageMapper;
    }

    @Override
    public Mono<Void> saveMessage(SaveMsgReq saveMsgReq) {
        long fromUserId = saveMsgReq.getFromUserId();
        long toUserId = saveMsgReq.getToUserId();
        long groupId = saveMsgReq.getGroupId();
        int messageType = saveMsgReq.getMessageType();
        String content = saveMsgReq.getContent();
        String fileUrl = saveMsgReq.getFileUrl();
        LoginUser loginUser = saveMsgReq.getLoginUser();
        String userId = loginUser.getUserId();
        TimerMessage timerMessage = new TimerMessage();
        timerMessage.setFromUserId(fromUserId);
        timerMessage.setToUserId(toUserId);
        timerMessage.setGroupId(groupId);
        timerMessage.setMessageType(messageType);
        timerMessage.setContent(content);
        timerMessage.setFileUrl(fileUrl);
        timerMessage.setSentTime(LocalDateTime.now());
        timerMessage.setCreateUser(userId);
        timerMessage.setUpdateUser(userId);
        timerMessageMapper.save(timerMessage);
        return Mono.empty();
    }
}
