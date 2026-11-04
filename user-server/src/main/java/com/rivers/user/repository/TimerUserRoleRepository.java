package com.rivers.user.repository;

import com.rivers.user.entity.TimerUserRole;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;

/**
 * 用户-角色关联 Repository（R2DBC）
 */
public interface TimerUserRoleRepository extends ReactiveCrudRepository<TimerUserRole, Long> {

    Flux<TimerUserRole> findByUserIdAndIsDeleted(String userId, Integer isDeleted);

    Flux<TimerUserRole> findByRoleCodeAndUserIdInAndIsDeleted(String roleCode, Collection<String> userIds,
                                                              Integer isDeleted);

    @Query("""
            SELECT COUNT(*) FROM timer_user_role WHERE is_deleted = 0 AND role_code = :roleCode
            AND (:userId = '' OR user_id LIKE CONCAT('%', :userId, '%'))
            """)
    Mono<Long> countByRoleCodeAndUserKeyword(String roleCode, String userId);

    @Query("""
            SELECT * FROM timer_user_role WHERE is_deleted = 0 AND role_code = :roleCode
            AND (:userId = '' OR user_id LIKE CONCAT('%', :userId, '%'))
            ORDER BY id DESC LIMIT :limit OFFSET :offset
            """)
    Flux<TimerUserRole> pageByRoleCodeAndUserKeyword(String roleCode, String userId, int limit, long offset);

    @Query("""
            SELECT COUNT(*) FROM timer_user_role WHERE is_deleted = 0 AND role_code = :roleCode
            AND user_id IN (:userIds)
            AND (:userId = '' OR user_id LIKE CONCAT('%', :userId, '%'))
            """)
    Mono<Long> countByRoleCodeAndUserIdIn(String roleCode, Collection<String> userIds, String userId);

    @Query("""
            SELECT * FROM timer_user_role WHERE is_deleted = 0 AND role_code = :roleCode
            AND user_id IN (:userIds)
            AND (:userId = '' OR user_id LIKE CONCAT('%', :userId, '%'))
            ORDER BY id DESC LIMIT :limit OFFSET :offset
            """)
    Flux<TimerUserRole> pageByRoleCodeAndUserIdIn(String roleCode, Collection<String> userIds,
                                                  String userId, int limit, long offset);

    @Modifying
    @Query("""
            UPDATE timer_user_role SET is_deleted = 1, update_time = NOW()
            WHERE role_code = :roleCode AND user_id IN (:userIds) AND is_deleted = 0
            """)
    Mono<Long> softDeleteByRoleCodeAndUserIdIn(String roleCode, Collection<String> userIds);
}
