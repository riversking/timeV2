package com.rivers.im.mapper;

import com.rivers.im.entity.TimerFriendRequest;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface TimerFriendRequestMapper extends ReactiveCrudRepository<TimerFriendRequest, Long> {

    /**
     * 查询待处理的好友请求
     */
    Flux<TimerFriendRequest> findByTargetUserIdAndStatus(String targetUserId, Integer status);

    /**
     * 查询单个好友请求
     */
    Mono<TimerFriendRequest> findByRequestUserIdAndTargetUserId(String requestUserId, String targetUserId);

    /**
     * 检查是否存在待处理的请求
     */
    Mono<Boolean> existsByRequestUserIdAndTargetUserIdAndStatus(String requestUserId, String targetUserId, Integer status);

    /**
     * 统计待处理请求数
     */
    Mono<Long> countByTargetUserIdAndStatus(String targetUserId, Integer status);
}