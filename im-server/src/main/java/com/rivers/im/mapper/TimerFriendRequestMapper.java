package com.rivers.im.mapper;

import com.rivers.im.entity.TimerFriendRequest;
import org.springframework.data.r2dbc.repository.Query;
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
    @Query("SELECT EXISTS(SELECT 1 FROM timer_friend_request " +
            "WHERE request_user_id = :requestUserId " +
            "AND target_user_id = :targetUserId " +
            "AND status = :status " +
            "AND is_deleted = 0)")
    Mono<Integer> existsTargetUserIdAndStatus(String requestUserId, String targetUserId, Integer status);

    /**
     * 统计待处理请求数
     */
    Mono<Long> countByTargetUserId(String targetUserId);

    @Query("SELECT * FROM timer_friend_request " +
            "WHERE target_user_id = :targetUserId " +
            "AND is_deleted = 0 " +
            "ORDER BY create_time DESC " +
            "LIMIT :limit OFFSET :offset")
    Flux<TimerFriendRequest> findPageByTargetUserIdAndStatus(
            String targetUserId, long limit, long offset);
}