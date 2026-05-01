package com.rivers.im.mapper;

import com.rivers.im.entity.TimerMessage;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface TimerMessageMapper extends ReactiveCrudRepository<TimerMessage, Long> {
}
