package com.rivers.nba.controller;

import com.rivers.core.vo.ResultVO;
import com.rivers.nba.GetDailyLeadersReq;
import com.rivers.nba.GetDailyLeadersRes;
import com.rivers.nba.GetPlayerStatLeadersReq;
import com.rivers.nba.GetPlayerStatLeadersRes;
import com.rivers.nba.GetTeamStandingsReq;
import com.rivers.nba.GetTeamStandingsRes;
import com.rivers.nba.service.IRankingService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * <p>
 * 排行榜 前端控制器（球队排名 / 球员数据排名，数据源 ESPN）
 * </p>
 *
 * @author rivers
 * @since 2026-10-15
 */
@RestController
@RequestMapping("ranking")
public class RankingController {

    private final IRankingService rankingService;

    public RankingController(IRankingService rankingService) {
        this.rankingService = rankingService;
    }

    /**
     * 球队排名（东西部/分区名次由服务端计算）
     */
    @PostMapping("getTeamStandings")
    public Mono<ResultVO<GetTeamStandingsRes>> getTeamStandings(@RequestBody GetTeamStandingsReq req) {
        return rankingService.getTeamStandings(req);
    }

    /**
     * 球员数据排名（按场均数值，命中率为百分比）
     */
    @PostMapping("getPlayerStatLeaders")
    public Mono<ResultVO<GetPlayerStatLeadersRes>> getPlayerStatLeaders(@RequestBody GetPlayerStatLeadersReq req) {
        return rankingService.getPlayerStatLeaders(req);
    }

    /**
     * 每日最佳（指定比赛日的单场数据榜，缺省取最近一个有完赛的比赛日）
     */
    @PostMapping("getDailyLeaders")
    public Mono<ResultVO<GetDailyLeadersRes>> getDailyLeaders(@RequestBody GetDailyLeadersReq req) {
        return rankingService.getDailyLeaders(req);
    }
}
