package com.rivers.nba.service;

import com.rivers.core.vo.ResultVO;
import com.rivers.nba.GetCurrentSeasonRes;
import reactor.core.publisher.Mono;

/**
 * 当前赛季信息表 服务类
 *
 * @author rivers
 * @since 2026-10-02
 */
public interface ICurrentSeasonService {

    /**
     * 同步当前赛季信息
     */
    Mono<ResultVO<Void>> syncCurrentSeason();

    /**
     * 查询当前赛季信息
     */
    Mono<ResultVO<GetCurrentSeasonRes>> getCurrentSeason();
}
