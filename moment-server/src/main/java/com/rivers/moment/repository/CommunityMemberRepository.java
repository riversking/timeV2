package com.rivers.moment.repository;

import com.rivers.moment.entity.CommunityMember;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * <p>
 * 球队社区成员 Repository（R2DBC）
 * </p>
 *
 * @author rivers
 * @since 2026-10-18
 */
public interface CommunityMemberRepository extends ReactiveCrudRepository<CommunityMember, Long> {

    /**
     * 加入社区（重复加入时恢复软删并刷新名称）
     */
    @Modifying
    @Query("""
            INSERT INTO moment_community_member
                (team_id, user_id, user_name, create_user, create_time, update_user, update_time, is_deleted)
            VALUES (:teamId, :userId, :userName, :userId, NOW(), :userId, NOW(), 0)
            ON DUPLICATE KEY UPDATE user_name = :userName, is_deleted = 0,
                update_user = :userId, update_time = NOW()
            """)
    Mono<Long> upsert(int teamId, String userId, String userName);

    /**
     * 退出社区
     */
    @Modifying
    @Query("""
            UPDATE moment_community_member SET is_deleted = 1, update_user = :userId, update_time = NOW()
            WHERE team_id = :teamId AND user_id = :userId AND is_deleted = 0
            """)
    Mono<Long> leave(int teamId, String userId);

    /**
     * 我加入的球队ID（按加入时间倒序）
     */
    @Query("""
            SELECT team_id FROM moment_community_member
            WHERE user_id = :userId AND is_deleted = 0 ORDER BY id DESC
            """)
    Flux<Integer> findTeamIdsByUserId(String userId);
}
