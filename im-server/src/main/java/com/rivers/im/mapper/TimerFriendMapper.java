package com.rivers.im.mapper;

import com.rivers.im.entity.TimerFriend;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface TimerFriendMapper extends ReactiveCrudRepository<TimerFriend, Long> {
}
