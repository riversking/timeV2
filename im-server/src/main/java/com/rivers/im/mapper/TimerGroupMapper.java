package com.rivers.im.mapper;

import com.rivers.im.entity.TimerGroup;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

public interface TimerGroupMapper extends ReactiveCrudRepository<TimerGroup, Long> {

    @Query("SELECT id, name, avatar, description,announcement, max_members, create_user, create_time " +
            "FROM timer_group " +
            "WHERE id IN (:groupIds) AND is_deleted = 0")
    Flux<TimerGroup> selectByIds(@Param("groupIds") List<Long> groupIds);

    @Query("UPDATE timer_group SET " +
            "announcement = :announcement, " +
            "update_user = :userId, " +
            "WHERE id = :groupId AND is_deleted = 0")
    Mono<Integer> updateAnnouncement(@Param("groupId") Long groupId,
                                     @Param("announcement") String announcement,
                                     @Param("userId") String userId);

}
