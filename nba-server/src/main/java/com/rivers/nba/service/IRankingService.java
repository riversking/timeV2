package com.rivers.nba.service;

import com.rivers.core.vo.ResultVO;
import com.rivers.nba.GetPlayerStatLeadersReq;
import com.rivers.nba.GetPlayerStatLeadersRes;
import com.rivers.nba.GetTeamStandingsReq;
import com.rivers.nba.GetTeamStandingsRes;
import reactor.core.publisher.Mono;

/**
 * <p>
 * 排行榜 服务接口（球队排名 / 球员数据排名）
 * </p>
 *
 * @author rivers
 * @since 2026-10-15
 */
public interface IRankingService {

    /**
     * 球队排名（东西部/分区排名由服务端按胜率计算）
     */
    Mono<ResultVO<GetTeamStandingsRes>> getTeamStandings(GetTeamStandingsReq req);

    /**
     * 球员数据排名（按场均数值，命中率为百分比）
     */
    Mono<ResultVO<GetPlayerStatLeadersRes>> getPlayerStatLeaders(GetPlayerStatLeadersReq req);

    /**
     * 同步排名数据落库（定时任务调用：球队排名 + 球员赛季统计全量）
     */
    Mono<ResultVO<Void>> syncRankings();
}
