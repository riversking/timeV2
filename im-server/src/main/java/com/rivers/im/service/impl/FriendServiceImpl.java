package com.rivers.im.service.impl;

import com.rivers.core.vo.ResultVO;
import com.rivers.im.entity.TimerFriend;
import com.rivers.im.mapper.TimerFriendMapper;
import com.rivers.im.service.IFriendService;
import com.rivers.proto.LoginUser;
import com.rivers.proto.SaveFriendReq;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class FriendServiceImpl implements IFriendService {

    private final TimerFriendMapper timerFriendMapper;

    public FriendServiceImpl(TimerFriendMapper timerFriendMapper) {
        this.timerFriendMapper = timerFriendMapper;
    }

    @Override
    public Mono<ResultVO<Void>> saveFriend(SaveFriendReq saveFriendReq) {
        String friendId = saveFriendReq.getFriendId();
        String remark = saveFriendReq.getRemark();
        LoginUser loginUser = saveFriendReq.getLoginUser();
        if (StringUtils.isBlank(friendId)) {
            return Mono.just(ResultVO.fail("friendId can not be null"));
        }
        String userId = loginUser.getUserId();
        TimerFriend timerFriend = TimerFriend.builder().build();
        timerFriend.setUserId(userId);
        timerFriend.setFriendId(friendId);
        timerFriend.setRemark(remark);
        timerFriend.setCreateUser(userId);
        timerFriend.setUpdateUser(userId);
        return timerFriendMapper.save(timerFriend)
                .thenReturn(ResultVO.<Void>ok())
                .onErrorResume(e -> {
                    log.error("保存好友失败", e); // 建议添加日志
                    return Mono.just(ResultVO.fail("save friend error"));
                });
    }
}
