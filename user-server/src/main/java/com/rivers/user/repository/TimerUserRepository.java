package com.rivers.user.repository;

import com.rivers.user.entity.TimerUser;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;

/**
 * 用户表 Repository（R2DBC）
 */
public interface TimerUserRepository extends ReactiveCrudRepository<TimerUser, Long> {

    Mono<TimerUser> findByUserIdAndIsDeleted(String userId, Integer isDeleted);

    Mono<Boolean> existsByUserIdAndIsDeleted(String userId, Integer isDeleted);

    Flux<TimerUser> findByUserIdInAndIsDeleted(Collection<String> userIds, Integer isDeleted);

    Flux<TimerUser> findByUsernameContainingAndIsDeleted(String username, Integer isDeleted);

    @Query("""
            SELECT COUNT(*) FROM timer_user WHERE is_deleted = 0
            AND (:userId = '' OR user_id LIKE CONCAT('%', :userId, '%'))
            AND (:username = '' OR username LIKE CONCAT('%', :username, '%'))
            """)
    Mono<Long> countByFilter(String userId, String username);

    @Query("""
            SELECT * FROM timer_user WHERE is_deleted = 0
            AND (:userId = '' OR user_id LIKE CONCAT('%', :userId, '%'))
            AND (:username = '' OR username LIKE CONCAT('%', :username, '%'))
            ORDER BY id DESC LIMIT :limit OFFSET :offset
            """)
    Flux<TimerUser> pageByFilter(String userId, String username, int limit, long offset);

    @Modifying
    @Query("""
            UPDATE timer_user SET username = :username, phone = :phone, mail = :mail,
            user_id = :userId, update_user = :updateUser, update_time = NOW()
            WHERE id = :id AND is_deleted = 0
            """)
    Mono<Long> updateUserInfo(long id, String username, String phone, String mail,
                              String userId, String updateUser);

    @Modifying
    @Query("""
            UPDATE timer_user SET is_disable = :isDisable, update_user = :updateUser, update_time = NOW()
            WHERE user_id = :userId AND is_deleted = 0
            """)
    Mono<Long> updateDisable(String userId, String isDisable, String updateUser);

    @Modifying
    @Query("""
            UPDATE timer_user SET password = :password, update_time = NOW()
            WHERE user_id = :userId AND is_deleted = 0
            """)
    Mono<Long> updatePassword(String userId, String password);

    @Modifying
    @Query("UPDATE timer_user SET is_deleted = 1, update_time = NOW() WHERE user_id = :userId AND is_deleted = 0")
    Mono<Long> softDeleteByUserId(String userId);
}
