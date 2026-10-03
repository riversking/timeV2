package com.rivers.nba.repository;

import com.rivers.nba.entity.TimerPlayer;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * <p>
 * 球员信息表 Repository（R2DBC）
 * </p>
 *
 * @author rivers
 * @since 2026-10-02
 */
public interface PlayerRepository extends ReactiveCrudRepository<TimerPlayer, Long> {

    /**
     * 分页查询（过滤逻辑删除，按球员全称模糊匹配）
     */
    @Query("SELECT * FROM player WHERE is_deleted = 0 AND draft_kings_name LIKE CONCAT('%', :name, '%') ORDER BY id LIMIT :limit OFFSET :offset")
    Flux<TimerPlayer> pageByName(String name, int limit, long offset);

    /**
     * 分页总数（过滤逻辑删除）
     */
    @Query("SELECT COUNT(*) FROM player WHERE is_deleted = 0 AND draft_kings_name LIKE CONCAT('%', :name, '%')")
    Mono<Long> countByName(String name);

    /**
     * 按球员ID查询单条（过滤逻辑删除）
     */
    @Query("SELECT * FROM player WHERE player_id = :playerId AND is_deleted = 0 LIMIT 1")
    Mono<TimerPlayer> findByPlayerId(long playerId);

    /**
     * 逻辑删除全表（is_deleted 置 1，与存量 MyBatis @TableLogic 语义对齐）
     */
    @Modifying
    @Query("UPDATE player SET is_deleted = 1 WHERE is_deleted = 0")
    Mono<Long> logicDeleteAll();
}
