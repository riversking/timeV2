package com.rivers.im.service.impl;

import com.rivers.core.vo.ResultVO;
import com.rivers.im.entity.TimerFriend;
import com.rivers.im.entity.TimerFriendRequest;
import com.rivers.im.entity.TimerUser;
import com.rivers.im.mapper.TimerFriendMapper;
import com.rivers.im.mapper.TimerFriendRequestMapper;
import com.rivers.im.mapper.TimerUserMapper;
import com.rivers.im.service.IFriendService;
import com.rivers.proto.*;
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
import java.util.Objects;
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
                                .setHasMore("0")
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
                                        .collect(Collectors.toMap(TimerUser::getUserId, v -> v,
                                                (a, _) -> a));
                                List<FriendRequestRes> list = i.stream()
                                        .map(f -> {
                                            TimerUser user = userMap.get(f.getOpponentId());
                                            if (user == null) {
                                                return FriendRequestRes.newBuilder()
                                                        .setFriendName("用户已注销")
                                                        .build();
                                            }
                                            return FriendRequestRes.newBuilder()
                                                    .setFriendId(user.getUserId())
                                                    .setFriendName(user.getUsername())
                                                    .setFriendAvatar(user.getAvatar())
                                                    .setRemark(f.getMessage())
                                                    .setStatus(Optional.ofNullable(TimerFriendRequest.Status
                                                                    .of(f.getStatus()))
                                                            .map(TimerFriendRequest.Status::getDesc)
                                                            .orElse(""))
                                                    .setDirection(Optional.ofNullable(TimerFriendRequest.Direction
                                                                    .of(f.getDirection()))
                                                            .map(TimerFriendRequest.Direction::getDesc)
                                                            .orElse(""))
                                                    .setUpdateTime(f.getCreateTime()
                                                            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                                                    .build();
                                        })
                                        .toList();
                                FriendRequestPageRes friendRequestPageRes = FriendRequestPageRes.newBuilder()
                                        .setHasMore(hasMore ? "1" : "0")
                                        .addAllFriendRequests(list)
                                        .build();
                                return ResultVO.ok(friendRequestPageRes);
                            });
                })
                .onErrorResume(e -> {
                    log.error("❌ [Friend] 获取好友请求分页失败: userId={}", userId, e);
                    return Mono.just(ResultVO.fail("获取好友请求失败"));
                });
    }

    @Override
    public Mono<ResultVO<FriendListRes>> getFriendList(FriendListReq friendListReq) {
        LoginUser loginUser = friendListReq.getLoginUser();
        String userId = loginUser.getUserId();
        Flux<TimerFriend> timerFriendFlux = friendMapper.selectAllFriendsByUserId(userId);
        return timerFriendFlux.collectList()
                .flatMap(i -> {
                    if (CollectionUtils.isEmpty(i)) {
                        return Mono.just(ResultVO.ok(FriendListRes.newBuilder().build()));
                    }
                    List<String> friendIds = i.stream()
                            .map(TimerFriend::getFriendId)
                            .distinct()
                            .toList();
                    Flux<TimerUser> timerUserFlux = userMapper.selectByUserIds(friendIds);
                    return timerUserFlux.collectList()
                            .map(u -> {
                                Map<String, TimerUser> userMap = u.stream()
                                        .collect(Collectors.toMap(TimerUser::getUserId, v -> v,
                                                (a, _) -> a));
                                List<FriendDetailRes> list = i.stream()
                                        .map(f -> {
                                            TimerUser user = userMap.get(f.getFriendId());
                                            if (user == null) {
                                                return FriendDetailRes.newBuilder()
                                                        .setFriendName("用户已注销")
                                                        .build();
                                            }
                                            return FriendDetailRes.newBuilder()
                                                    .setId(f.getId())
                                                    .setFriendId(user.getUserId())
                                                    .setFriendName(user.getUsername())
                                                    .setFriendAvatar(user.getAvatar())
                                                    .setRemark(f.getRemark())
                                                    .build();
                                        })
                                        .toList();
                                FriendListRes friendListRes = FriendListRes.newBuilder()
                                        .addAllFriendList(list)
                                        .build();
                                return ResultVO.ok(friendListRes);
                            });
                })
                .onErrorResume(e -> {
                    log.error("❌ [Friend] 获取好友列表失败: userId={}", userId, e);
                    return Mono.just(ResultVO.fail("获取好友列表失败"));
                });
    }
}