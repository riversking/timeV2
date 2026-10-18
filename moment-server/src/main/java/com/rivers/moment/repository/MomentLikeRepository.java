package com.rivers.moment.repository;

import com.rivers.moment.entity.MomentLike;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;

/**
 * <p>
 * 动态点赞表 Repository（R2DBC）
 * </p>
 *
 * @author rivers
 * @since 2026-10-19
 */
public interface MomentLikeRepository extends ReactiveCrudRepository<MomentLike, Long> {

    Mono<MomentLike> findByMomentIdAndUserId(long momentId, String userId);

    Flux<MomentLike> findByMomentIdInAndUserIdAndIsDeleted(Collection<Long> momentIds,
                                                           String userId, int isDeleted);

    Mono<Long> countByMomentIdAndIsDeleted(long momentId, int isDeleted);

    /**
     * 原子切换点赞态：唯一键冲突时翻转 is_deleted，避免并发重复点赞
     */
    @Modifying
    @Query("""
            INSERT INTO moment_like (moment_id, user_id, user_name, create_user, create_time,
                                     update_user, update_time, is_deleted)
            VALUES (:momentId, :userId, :userName, :userId, NOW(), :userId, NOW(), 0)
            ON DUPLICATE KEY UPDATE is_deleted = 1 - is_deleted,
                                    user_name = VALUES(user_name),
                                    update_user = :userId,
                                    update_time = NOW()
            """)
    Mono<Long> toggle(long momentId, String userId, String userName);
}
