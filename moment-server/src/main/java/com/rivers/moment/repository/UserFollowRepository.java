package com.rivers.moment.repository;

import com.rivers.moment.entity.UserFollow;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * <p>
 * 用户关注关系 Repository（R2DBC）
 * </p>
 *
 * @author rivers
 * @since 2026-10-18
 */
public interface UserFollowRepository extends ReactiveCrudRepository<UserFollow, Long> {

    /**
     * 关注用户（重复关注时恢复软删并刷新双方名称）
     */
    @Modifying
    @Query("""
            INSERT INTO moment_user_follow
                (follower_id, follower_name, followee_id, followee_name,
                 create_user, create_time, update_user, update_time, is_deleted)
            VALUES (:followerId, :followerName, :followeeId, :followeeName,
                    :followerId, NOW(), :followerId, NOW(), 0)
            ON DUPLICATE KEY UPDATE follower_name = :followerName, followee_name = :followeeName,
                is_deleted = 0, update_user = :followerId, update_time = NOW()
            """)
    Mono<Long> upsert(String followerId, String followerName, String followeeId, String followeeName);

    /**
     * 取消关注
     */
    @Modifying
    @Query("""
            UPDATE moment_user_follow SET is_deleted = 1, update_user = :followerId, update_time = NOW()
            WHERE follower_id = :followerId AND followee_id = :followeeId AND is_deleted = 0
            """)
    Mono<Long> unfollow(String followerId, String followeeId);

    /**
     * 我关注的人（按关注时间倒序）
     */
    @Query("""
            SELECT * FROM moment_user_follow
            WHERE follower_id = :followerId AND is_deleted = 0 ORDER BY id DESC
            """)
    Flux<UserFollow> findFollowing(String followerId);

    /**
     * 我的粉丝（按关注时间倒序）
     */
    @Query("""
            SELECT * FROM moment_user_follow
            WHERE followee_id = :followeeId AND is_deleted = 0 ORDER BY id DESC
            """)
    Flux<UserFollow> findFollowers(String followeeId);
}
