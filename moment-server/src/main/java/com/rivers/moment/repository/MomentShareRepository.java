package com.rivers.moment.repository;

import com.rivers.moment.entity.MomentShare;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * <p>
 * 动态分享表 Repository（R2DBC）
 * </p>
 *
 * @author rivers
 * @since 2026-10-21
 */
public interface MomentShareRepository extends ReactiveCrudRepository<MomentShare, Long> {

    /**
     * 同一用户重复分享幂等：唯一键冲突时恢复记录并刷新分享时间
     */
    @Modifying
    @Query("""
            INSERT INTO moment_share (moment_id, user_id, user_name, create_user, create_time,
                                      update_user, update_time, is_deleted)
            VALUES (:momentId, :userId, :userName, :userId, NOW(), :userId, NOW(), 0)
            ON DUPLICATE KEY UPDATE is_deleted = 0,
                                    user_name = :userName,
                                    update_user = :userId,
                                    update_time = NOW()
            """)
    Mono<Long> upsert(long momentId, String userId, String userName);

    /**
     * 分享人列表：按分享时间倒序
     */
    @Query("""
            SELECT * FROM moment_share
            WHERE moment_id = :momentId AND is_deleted = 0
            ORDER BY update_time DESC, id DESC LIMIT :limit
            """)
    Flux<MomentShare> findShareUsers(long momentId, int limit);

    Mono<Long> countByMomentIdAndIsDeleted(long momentId, int isDeleted);
}
