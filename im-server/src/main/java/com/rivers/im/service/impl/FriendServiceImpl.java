package com.rivers.im.service.impl;

import com.rivers.core.vo.ResultVO;
import com.rivers.im.entity.TimerFriendRequest;
import com.rivers.im.entity.TimerUser;
import com.rivers.im.mapper.TimerFriendMapper;
import com.rivers.im.mapper.TimerFriendRequestMapper;
import com.rivers.im.mapper.TimerUserMapper;
import com.rivers.im.service.IFriendService;
import com.rivers.proto.FriendRequestPageReq;
import com.rivers.proto.FriendRequestPageRes;
import com.rivers.proto.FriendRequestRes;
import com.rivers.proto.LoginUser;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
public class FriendServiceImpl implements IFriendService {

    private final TimerFriendRequestMapper friendRequestMapper;

    private final TimerFriendMapper friendMapper;

    private final TimerUserMapper userMapper;

    public FriendServiceImpl(TimerFriendRequestMapper friendRequestMapper, TimerFriendMapper friendMapper,
                             TimerUserMapper userMapper) {
        this.friendRequestMapper = friendRequestMapper;
        this.friendMapper = friendMapper;
        this.userMapper = userMapper;
    }

    @Override
    public Mono<ResultVO<FriendRequestPageRes>> getFriendRequestPage(FriendRequestPageReq friendRequestPageReq) {
        int pageSize = friendRequestPageReq.getPageSize();
        String lastCreateTime = friendRequestPageReq.getLastCreateTime();
        long lastId = friendRequestPageReq.getLastId();
        LoginUser loginUser = friendRequestPageReq.getLoginUser();
        String userId = loginUser.getUserId();
        Flux<TimerFriendRequest> dataFlux = friendRequestMapper.selectFriendRequestByPage(
                userId,
                StringUtils.isNotBlank(lastCreateTime) ?
                        LocalDateTime.parse(lastCreateTime, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                        : LocalDateTime.of(9999, 12, 31, 23, 59, 59),
                lastId == 0 ? Long.MAX_VALUE : lastId,
                pageSize);
        return dataFlux.collectList()
                .flatMap(i -> {
                    if (CollectionUtils.isEmpty(i)) {
                        return Mono.just(ResultVO.ok(FriendRequestPageRes.newBuilder()
                                .setHasMore(0)
                                .build()));
                    }
                    boolean hasMore = i.size() == pageSize;
                    List<String> opponentIds = i.stream()
                            .map(TimerFriendRequest::getOpponentId)
                            .distinct()
                            .toList();
                    Flux<TimerUser> timerUserFlux = userMapper.selectByUserIds(opponentIds);
                    return timerUserFlux.collectList()
                            .map(u -> {
                                Map<String, TimerUser> userMap = u.stream()
                                        .collect(Collectors.toMap(TimerUser::getUserId, v -> v));
                                List<FriendRequestRes> list = i.stream()
                                        .map(f -> {
                                            TimerUser user = userMap.getOrDefault(f.getOpponentId(), new TimerUser());
                                            return FriendRequestRes.newBuilder()
                                                    .setFriendId(user.getUserId())
                                                    .setFriendName(user.getUsername())
                                                    .setFriendAvatar(user.getAvatar())
                                                    .setRemark(f.getMessage())
                                                    .setStatus(Optional.ofNullable(TimerFriendRequest.Status
                                                                    .of(f.getStatus()))
                                                            .map(TimerFriendRequest.Status::getDesc)
                                                            .orElse(""))
                                                    .build();
                                        })
                                        .toList();
                                FriendRequestPageRes friendRequestPageRes = FriendRequestPageRes.newBuilder()
                                        .setHasMore(hasMore ? 1 : 0)
                                        .addAllFriendRequests(list)
                                        .build();
                                return ResultVO.ok(friendRequestPageRes);
                            });
                });
    }
}
