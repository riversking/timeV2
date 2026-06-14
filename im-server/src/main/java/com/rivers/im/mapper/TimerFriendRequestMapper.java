package com.rivers.im.mapper;

import com.rivers.im.entity.TimerFriendRequest;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

public interface TimerFriendRequestMapper extends ReactiveCrudRepository<TimerFriendRequest, Long> {

    /**
     * 根据 relationId 批量更新状态（一条 SQL 同时更新发送方和接收方的记录）
     */
    @Query("UPDATE timer_friend_request SET status = :status, update_user = :userId " +
            "WHERE relation_id = :relationId AND is_deleted = 0")
    Mono<Integer> updateStatusByRelationId(
            @Param("relationId") Long relationId,
            @Param("userId") String userId,
            @Param("status") Integer status);


    /**
     * 检查是否存在待处理的请求
     */
    @Query("SELECT EXISTS(SELECT 1 FROM timer_friend_request " +
            "WHERE user_id = :userId AND opponent_id = :opponentId" +
            "AND status = 1 " +
            "AND is_deleted = 0)")
    Mono<Integer> existsPendingBetweenUsers(String userId, String opponentId);


    @Query("""
            SELECT id,user_id,opponent_id,direction,status,message,update_time\s
             FROM timer_friend_request
            WHERE user_id = :userId\s
              AND is_deleted = 0
              AND (create_time, id) < (:lastCreateTime, :lastId)
            ORDER BY create_time DESC, id DESC
            LIMIT :limit;""")
    Flux<TimerFriendRequest> selectFriendRequestByPage(
            @Param("userId") String userId,
            @Param("lastCreateTime") LocalDateTime lastCreateTime,
            @Param("lastId") Long lastId,
            @Param("limit") Integer limit);

    @Query("SELECT user_id,opponent_id,direction,status,message " +
            "FROM timer_friend_request " +
            "WHERE user_id = :userId AND opponent_id = :opponentId " +
            "AND is_deleted = 0 LIMIT 1")
    Mono<TimerFriendRequest> selectByUserIdAndOpponentId(@Param("userId") String userId,
                                                       @Param("opponentId") String opponentId);

    /**
     * 通过 relationId 批量更新 update_time（同时更新发送方和接收方）
     */
    @Query("UPDATE timer_friend_request SET update_time = :updateTime " +
            "WHERE relation_id = :relationId AND is_deleted = 0")
    Mono<Integer> updateTimeByRelationId(@Param("relationId") Long relationId,
                                         @Param("updateTime") LocalDateTime updateTime);

    /**
     * 通过 relationId 和 userId 查找接收方记录
     */
    @Query("SELECT user_id,opponent_id,direction,status,message FROM timer_friend_request " +
            "WHERE relation_id = :relationId AND user_id = :userId " +
            "AND is_deleted = 0 LIMIT 1")
    Mono<TimerFriendRequest> selectByRelationIdAndUserId(@Param("relationId") Long relationId,
                                                       @Param("userId") String userId);
}