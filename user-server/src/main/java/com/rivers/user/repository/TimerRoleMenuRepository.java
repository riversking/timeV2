package com.rivers.user.repository;

import com.rivers.user.entity.TimerRoleMenu;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;

/**
 * 角色-菜单关联 Repository（R2DBC）
 */
public interface TimerRoleMenuRepository extends ReactiveCrudRepository<TimerRoleMenu, Long> {

    Flux<TimerRoleMenu> findByRoleCodeAndIsDeleted(String roleCode, Integer isDeleted);

    Flux<TimerRoleMenu> findByRoleCodeInAndIsDeleted(Collection<String> roleCodes, Integer isDeleted);

    @Modifying
    @Query("UPDATE timer_role_menu SET is_deleted = 1, update_time = NOW() WHERE role_code = :roleCode AND is_deleted = 0")
    Mono<Long> softDeleteByRoleCode(String roleCode);

    @Modifying
    @Query("""
            UPDATE timer_role_menu SET is_deleted = 1, update_time = NOW()
            WHERE role_code = :roleCode AND menu_code IN (:menuCodes) AND is_deleted = 0
            """)
    Mono<Long> softDeleteByRoleCodeAndMenuCodeIn(String roleCode, Collection<String> menuCodes);
}
