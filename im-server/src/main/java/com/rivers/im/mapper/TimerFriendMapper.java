package com.rivers.im.mapper;

import com.rivers.im.entity.TimerFriend;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface TimerFriendMapper extends ReactiveCrudRepository<TimerFriend, Long> {


    @Query("SELECT id, user_id, friend_id,remark " +
            "FROM timer_friend " +
            "WHERE user_id = :userId " +
            "  AND is_deleted = 0")
    Flux<TimerFriend> selectAllFriendsByUserId(@Param("userId") String userId);
}
