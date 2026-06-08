package com.rivers.im.mapper;

import com.rivers.im.entity.TimerUser;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

import java.util.List;

public interface TimerUserMapper extends ReactiveCrudRepository<TimerUser, Long> {


    @Query("SELECT user_id, username,avatar " +
            "FROM timer_user " +
            "WHERE user_id IN :userIds")
    Flux<TimerUser> selectByUserIds(List<String> userIds);

}
