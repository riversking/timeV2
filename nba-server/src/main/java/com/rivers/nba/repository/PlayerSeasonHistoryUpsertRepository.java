package com.rivers.nba.repository;

import com.rivers.nba.entity.PlayerSeasonHistory;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * <p>
 * 球员历年数据批量写入：多行 VALUES 的占位符数量随行数变化，无法用 @Query 表达，
 * 由 RepositoryImpl 统一承载 SQL
 * </p>
 *
 * @author rivers
 * @since 2026-11-02
 */
public interface PlayerSeasonHistoryUpsertRepository {

    /**
     * 批量 upsert（按 season + season_type + player_id 唯一键），每批 300 行
     */
    Mono<Void> upsertAll(List<PlayerSeasonHistory> rows);
}
