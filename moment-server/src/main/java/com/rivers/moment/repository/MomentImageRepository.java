package com.rivers.moment.repository;

import com.rivers.moment.entity.MomentImage;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;

/**
 * <p>
 * 动态图片表 Repository（R2DBC）
 * </p>
 *
 * @author rivers
 * @since 2026-10-17
 */
public interface MomentImageRepository extends ReactiveCrudRepository<MomentImage, Long> {

    /**
     * 批量查询动态图片，按动态分组顺序与图片展示顺序返回
     */
    Flux<MomentImage> findByMomentIdInOrderBySortAscIdAsc(Collection<Long> momentIds);

    /**
     * 随动态软删除：返回受影响行数
     */
    @Modifying
    @Query("""
            UPDATE moment_image SET is_deleted = 1, update_user = :userId, update_time = NOW()
            WHERE moment_id = :momentId AND is_deleted = 0
            """)
    Mono<Long> softDeleteByMomentId(long momentId, String userId);
}
