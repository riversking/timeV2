package com.rivers.nba.service;

import com.rivers.nba.entity.PlayerLeagueStat;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * <p>
 * 球员赛季联盟对比 服务接口（同步时预计算落库，查询只读库）
 * </p>
 *
 * @author rivers
 * @since 2026-11-01
 */
public interface ILeagueCompareService {

    /**
     * 球员全部赛季的联盟对比行
     */
    Flux<PlayerLeagueStat> findByPlayerId(long playerId);

    /**
     * 按库内该赛季球员统计重算并整季替换对比行
     */
    Mono<Void> rebuildSeason(int season);

    /**
     * 异步重算指定赛季（不阻塞调用方）
     */
    void rebuildSeasonAsync(int season);

    /**
     * 补齐缺失的赛季对比数据（定时任务执行，只读库）
     */
    Mono<Void> backfillMissing();
}
