package com.rivers.nba.service;

import com.rivers.nba.entity.PlayerSeasonHistory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * <p>
 * 球员历年数据服务：季前赛/常规赛/季后赛三类统计的落库、回填与刷新
 * </p>
 *
 * @author rivers
 * @since 2026-11-01
 */
public interface IPlayerSeasonHistoryService {

    /**
     * 球员全部历年数据（按赛季倒序，赛季内按赛程分类升序）
     */
    Flux<PlayerSeasonHistory> findByPlayerId(long playerId);

    /**
     * 历史赛季（不含当季）常规赛数据是否均已落库
     */
    Mono<Boolean> isHistoryInitialized();

    /**
     * 补齐近若干赛季缺失的赛季分类数据（初始化任务执行，已存在则跳过）
     */
    Mono<Void> backfillMissing();

    /**
     * 定时任务入口：刷新当季常规赛/季后赛，并在季前赛窗口内刷新季前赛
     */
    Mono<Void> syncRecent();
}
