package com.rivers.moment.repository;

import com.rivers.moment.entity.Moment;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * <p>
 * 用户动态表 Repository（R2DBC）
 * </p>
 *
 * @author rivers
 * @since 2026-10-17
 */
public interface MomentRepository extends ReactiveCrudRepository<Moment, Long> {

    /**
     * 动态分页（userId 为空取全部，否则仅该用户；teamId=0 仅主动态，>0 仅该球队社区文章；
     * 两类内容严格隔离；按发布时间倒序）
     */
    @Query("""
            SELECT * FROM moment WHERE is_deleted = 0
            AND (:userId = '' OR user_id = :userId)
            AND team_id = :teamId
            ORDER BY id DESC LIMIT :limit OFFSET :offset
            """)
    Flux<Moment> pageByFilter(String userId, int teamId, int limit, long offset);

    @Query("""
            SELECT COUNT(*) FROM moment WHERE is_deleted = 0
            AND (:userId = '' OR user_id = :userId)
            AND team_id = :teamId
            """)
    Mono<Long> countByFilter(String userId, int teamId);

    /**
     * 软删除（仅作者本人可删，返回受影响行数，0 表示动态不存在或无权删除）
     */
    @Modifying
    @Query("""
            UPDATE moment SET is_deleted = 1, update_user = :userId, update_time = NOW()
            WHERE id = :momentId AND user_id = :userId AND is_deleted = 0
            """)
    Mono<Long> softDelete(long momentId, String userId);

    /**
     * 按点赞明细重算冗余点赞数，避免并发增减产生漂移
     */
    @Modifying
    @Query("""
            UPDATE moment SET like_count = (
                SELECT COUNT(*) FROM moment_like WHERE moment_id = :momentId AND is_deleted = 0
            ) WHERE id = :momentId
            """)
    Mono<Long> refreshLikeCount(long momentId);

    /**
     * 按评论明细重算冗余评论数
     */
    @Modifying
    @Query("""
            UPDATE moment SET comment_count = (
                SELECT COUNT(*) FROM moment_comment WHERE moment_id = :momentId AND is_deleted = 0
            ) WHERE id = :momentId
            """)
    Mono<Long> refreshCommentCount(long momentId);
}
