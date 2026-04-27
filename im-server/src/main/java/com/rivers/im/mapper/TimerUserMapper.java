package com.rivers.im.mapper;

import com.rivers.im.entity.TimerUser;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface TimerUserMapper extends ReactiveCrudRepository<TimerUser, Long> {

}
