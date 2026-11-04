package com.rivers.user.repository;

import com.rivers.user.entity.TimerDic;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 字典表 Repository（R2DBC）
 */
public interface TimerDicRepository extends ReactiveCrudRepository<TimerDic, Long> {

    Mono<TimerDic> findByDicKeyAndIsDeleted(String dicKey, Integer isDeleted);

    Mono<Boolean> existsByDicKeyAndIsDeleted(String dicKey, Integer isDeleted);

    Flux<TimerDic> findAllByIsDeletedOrderBySortAsc(Integer isDeleted);

    Flux<TimerDic> findByParentIdAndIsDeletedOrderBySortAsc(Long parentId, Integer isDeleted);

    @Modifying
    @Query("""
            UPDATE timer_dic SET dic_key = :dicKey, dic_value = :dicValue, dic_desc = :dicDesc,
            parent_id = :parentId, sort = :sort, update_user = :updateUser, update_time = NOW()
            WHERE id = :id AND is_deleted = 0
            """)
    Mono<Long> updateDic(long id, String dicKey, String dicValue, String dicDesc,
                         long parentId, int sort, String updateUser);

    @Modifying
    @Query("UPDATE timer_dic SET is_deleted = 1, update_time = NOW() WHERE id = :id AND is_deleted = 0")
    Mono<Long> softDeleteById(long id);
}
