package com.rivers.moment.repository;

import com.rivers.moment.entity.MomentComment;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * <p>
 * 动态评论表 Repository（R2DBC）
 * </p>
 *
 * @author rivers
 * @since 2026-10-19
 */
public interface MomentCommentRepository extends ReactiveCrudRepository<MomentComment, Long> {

    @Query("""
            SELECT * FROM moment_comment
            WHERE moment_id = :momentId AND parent_id = 0 AND is_deleted = 0
            ORDER BY id DESC LIMIT :limit OFFSET :offset
            """)
    Flux<MomentComment> pageTopLevelByMomentId(long momentId, int limit, long offset);

    @Query("""
            SELECT * FROM moment_comment
            WHERE moment_id = :momentId AND parent_id > 0 AND is_deleted = 0
            ORDER BY id ASC
            """)
    Flux<MomentComment> findAllRepliesByMomentId(long momentId);

    Mono<Long> countByMomentIdAndIsDeleted(long momentId, int isDeleted);

    Mono<Long> countByMomentIdAndParentIdAndIsDeleted(long momentId, long parentId, int isDeleted);
}
