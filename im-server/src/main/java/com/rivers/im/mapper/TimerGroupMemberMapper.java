package com.rivers.im.mapper;

import com.rivers.im.entity.TimerGroupMember;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface TimerGroupMemberMapper extends ReactiveCrudRepository<TimerGroupMember, Long> {

    @Query("SELECT id, group_id, user_id, role, nickname, joined_at " +
            "FROM timer_group_member " +
            "WHERE group_id = :groupId " +
            "  AND is_deleted = 0")
    Flux<TimerGroupMember> selectByGroupId(@Param("groupId") Long groupId);

    @Query("SELECT group_id FROM timer_group_member " +
            "WHERE user_id = :userId " +
            "  AND is_deleted = 0")
    Flux<Long> selectGroupIdsByUserId(@Param("userId") String userId);

    @Query("SELECT id, group_id, user_id, role, nickname, joined_at " +
            "FROM timer_group_member " +
            "WHERE group_id = :groupId " +
            "  AND user_id = :userId " +
            " AND is_deleted = 0 " +
            "LIMIT 1")
    Mono<TimerGroupMember> selectByGroupIdAndUserId(@Param("groupId") Long groupId,
                                                    @Param("userId") String userId);

    @Query("UPDATE timer_group_member SET " +
            "is_deleted = 1, " +
            "update_user = :operator " +
            "WHERE group_id = :groupId " +
            "  AND user_id = :userId " +
            "AND is_deleted = 0")
    Mono<Integer> deleteMember(@Param("groupId") Long groupId,
                                   @Param("userId") String userId,
                                   @Param("operator") String operator);

    @Query("UPDATE timer_group_member SET " +
            " is_deleted = 1," +
            " update_user = :operator " +
            "WHERE group_id = :groupId " +
            "  AND is_deleted = 0")
    Mono<Integer> deleteAllMembers(@Param("groupId") Long groupId,
                                       @Param("operator") String operator);

    @Query("SELECT COUNT(*) FROM timer_group_member " +
            "WHERE group_id = :groupId AND is_deleted = 0")
    Mono<Integer> selectMembersCount(@Param("groupId") Long groupId);
}
