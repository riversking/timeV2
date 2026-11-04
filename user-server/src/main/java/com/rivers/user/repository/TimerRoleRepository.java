package com.rivers.user.repository;

import com.rivers.user.entity.TimerRole;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 角色表 Repository（R2DBC）
 */
public interface TimerRoleRepository extends ReactiveCrudRepository<TimerRole, Long> {

    Mono<TimerRole> findByRoleCodeAndIsDeleted(String roleCode, Integer isDeleted);

    Mono<Boolean> existsByRoleCodeAndIsDeleted(String roleCode, Integer isDeleted);

    @Query("""
            SELECT COUNT(*) FROM timer_role WHERE is_deleted = 0
            AND (:roleCode = '' OR role_code LIKE CONCAT('%', :roleCode, '%'))
            AND (:roleName = '' OR role_name LIKE CONCAT('%', :roleName, '%'))
            """)
    Mono<Long> countByFilter(String roleCode, String roleName);

    @Query("""
            SELECT * FROM timer_role WHERE is_deleted = 0
            AND (:roleCode = '' OR role_code LIKE CONCAT('%', :roleCode, '%'))
            AND (:roleName = '' OR role_name LIKE CONCAT('%', :roleName, '%'))
            ORDER BY id DESC LIMIT :limit OFFSET :offset
            """)
    Flux<TimerRole> pageByFilter(String roleCode, String roleName, int limit, long offset);

    @Modifying
    @Query("""
            UPDATE timer_role SET role_code = :roleCode, role_name = :roleName,
            update_user = :updateUser, update_time = NOW()
            WHERE id = :id AND is_deleted = 0
            """)
    Mono<Long> updateRole(long id, String roleCode, String roleName, String updateUser);

    @Modifying
    @Query("UPDATE timer_role SET is_deleted = 1, update_time = NOW() WHERE id = :id AND is_deleted = 0")
    Mono<Long> softDeleteById(long id);
}
