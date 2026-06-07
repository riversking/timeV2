package com.rivers.im.service.impl;

import com.rivers.core.vo.ResultVO;
import com.rivers.im.entity.TimerFriendRequest;
import com.rivers.im.mapper.TimerFriendMapper;
import com.rivers.im.mapper.TimerFriendRequestMapper;
import com.rivers.im.service.IFriendService;
import com.rivers.proto.FriendRequestPageReq;
import com.rivers.proto.FriendRequestPageRes;
import com.rivers.proto.LoginUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
@Slf4j
public class FriendServiceImpl implements IFriendService {

    private final TimerFriendRequestMapper friendRequestMapper;

    private final TimerFriendMapper friendMapper;

    public FriendServiceImpl(TimerFriendRequestMapper friendRequestMapper, TimerFriendMapper friendMapper) {
        this.friendRequestMapper = friendRequestMapper;
        this.friendMapper = friendMapper;
    }

    @Override
    public Mono<ResultVO<FriendRequestPageRes>> getFriendRequestPage(FriendRequestPageReq friendRequestPageReq) {
        int currentPage = friendRequestPageReq.getCurrentPage();
        int pageSize = friendRequestPageReq.getPageSize();
        LoginUser loginUser = friendRequestPageReq.getLoginUser();
        String userId = loginUser.getUserId();
        Mono<Long> countMono = friendRequestMapper.countByTargetUserId(userId);
        Flux<TimerFriendRequest> dataFlux = friendRequestMapper.findPageByTargetUserIdAndStatus(userId, currentPage,pageSize);
        return Mono.zip(countMono, dataFlux.collectList())
                .map(tuple -> {
                    long total = tuple.getT1();
                    List<TimerFriendRequest> list = tuple.getT2();
                    FriendRequestPageRes.Builder builder = FriendRequestPageRes.newBuilder().setTotal(total);
                    return ResultVO.ok(builder.build());
                })
                .doOnSuccess(r -> log.info("👥 好友请求分页查询: userId={}, total={}",
                        userId, r.getData() != null ? r.getData().getTotal() : 0))
                .onErrorResume(e -> {
                    log.error("❌ 好友请求分页查询失败: userId={}", userId, e);
                    return Mono.just(ResultVO.fail("查询好友请求失败"));
                });
    }
}
