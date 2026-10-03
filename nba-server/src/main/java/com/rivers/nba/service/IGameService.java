package com.rivers.nba.service;

import com.rivers.core.vo.ResultVO;
import com.rivers.nba.GetGameCalendarReq;
import com.rivers.nba.GetGameCalendarRes;
import com.rivers.nba.GetGamesReq;
import com.rivers.nba.GetGamesRes;
import com.rivers.nba.SyncGamesByDateReq;
import com.rivers.nba.SyncGamesBySeasonReq;
import reactor.core.publisher.Mono;

/**
 * 比赛信息表 服务类
 *
 * @author rivers
 * @since 2026-10-02
 */
public interface IGameService {

    /**
     * 按赛季同步比赛
     */
    Mono<ResultVO<Void>> syncGamesBySeason(SyncGamesBySeasonReq req);

    /**
     * 按日期同步比赛
     */
    Mono<ResultVO<Void>> syncGamesByDate(SyncGamesByDateReq req);

    /**
     * 分页查询比赛（season/date/teamId 可选过滤，按比赛日正序）
     */
    Mono<ResultVO<GetGamesRes>> getGamePage(GetGamesReq req);

    /**
     * 查询最近的比赛（比赛日不早于当前时间，按比赛日正序）
     */
    Mono<ResultVO<GetGamesRes>> getRecentGames();

    /**
     * 查询比赛日历（按月汇总每天场次，season/teamId 可选过滤）
     */
    Mono<ResultVO<GetGameCalendarRes>> getGameCalendar(GetGameCalendarReq req);
}
