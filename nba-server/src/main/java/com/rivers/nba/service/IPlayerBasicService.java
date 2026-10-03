package com.rivers.nba.service;

import com.rivers.core.vo.ResultVO;
import com.rivers.nba.GetPlayerBasicsReq;
import com.rivers.nba.GetPlayerBasicsRes;
import com.rivers.nba.SyncPlayersByTeamReq;
import reactor.core.publisher.Mono;

/**
 * 球员简档信息表 服务类
 *
 * @author rivers
 * @since 2026-10-02
 */
public interface IPlayerBasicService {

    /**
     * 同步现役球员简档（source=1）
     */
    Mono<ResultVO<Void>> syncActivePlayers();

    /**
     * 同步自由球员简档（source=2）
     */
    Mono<ResultVO<Void>> syncFreeAgents();

    /**
     * 按球队同步球员简档（source=3）
     */
    Mono<ResultVO<Void>> syncPlayersByTeam(SyncPlayersByTeamReq req);

    /**
     * 同步自由球员全量（source=4）
     */
    Mono<ResultVO<Void>> syncFreeAgentsFull();

    /**
     * 分页查询球员简档（按 source + 姓名模糊）
     */
    Mono<ResultVO<GetPlayerBasicsRes>> getPlayerBasicPage(GetPlayerBasicsReq req);
}
