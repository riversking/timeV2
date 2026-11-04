package com.rivers.user.repository;

import com.rivers.user.entity.TimerMenu;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;

/**
 * 菜单表 Repository（R2DBC）
 */
public interface TimerMenuRepository extends ReactiveCrudRepository<TimerMenu, Long> {

    Mono<TimerMenu> findByMenuCodeAndIsDeleted(String menuCode, Integer isDeleted);

    Mono<Boolean> existsByMenuCodeAndIsDeleted(String menuCode, Integer isDeleted);

    Flux<TimerMenu> findByMenuCodeInAndIsDeleted(Collection<String> menuCodes, Integer isDeleted);

    Flux<TimerMenu> findAllByIsDeleted(Integer isDeleted);

    /**
     * 门户菜单：归属=门户 + 启用 + 已配置路由，按排序号升序
     */
    @Query("""
            SELECT * FROM timer_menu WHERE is_deleted = 0 AND menu_scope = :menuScope AND status = :status
            AND route_path IS NOT NULL AND route_path <> ''
            ORDER BY sort_order ASC
            """)
    Flux<TimerMenu> findPortalMenus(int menuScope, int status);

    @Modifying
    @Query("""
            UPDATE timer_menu SET menu_code = :menuCode, menu_name = :menuName, menu_type = :menuType,
            menu_scope = :menuScope, icon = :icon, parent_id = :parentId, route_path = :routePath,
            permission_code = :permissionCode, component = :component, sort_order = :sortOrder,
            update_user = :updateUser, update_time = NOW()
            WHERE id = :id AND is_deleted = 0
            """)
    Mono<Long> updateMenu(long id, String menuCode, String menuName, int menuType, int menuScope,
                          String icon, long parentId, String routePath, String permissionCode,
                          String component, int sortOrder, String updateUser);

    @Modifying
    @Query("UPDATE timer_menu SET is_deleted = 1, update_time = NOW() WHERE menu_code = :menuCode AND is_deleted = 0")
    Mono<Long> softDeleteByMenuCode(String menuCode);

    @Modifying
    @Query("""
            UPDATE timer_menu SET is_deleted = 1, update_time = NOW()
            WHERE menu_code IN (:menuCodes) AND is_deleted = 0
            """)
    Mono<Long> softDeleteByMenuCodeIn(Collection<String> menuCodes);
}
