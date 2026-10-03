package com.rivers.nba.controller;

import com.rivers.core.vo.ResultVO;
import com.rivers.nba.GetGameCalendarReq;
import com.rivers.nba.GetGameCalendarRes;
import com.rivers.nba.GetGamesReq;
import com.rivers.nba.GetGamesRes;
import com.rivers.nba.SyncGamesByDateReq;
import com.rivers.nba.SyncGamesBySeasonReq;
import com.rivers.nba.service.IGameService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * <p>
 * 比赛信息表 前端控制器
 * </p>
 *
 * @author rivers
 * @since 2026-10-02
 */
@RestController
@RequestMapping("/game")
public class GameController {

    private final IGameService gameService;

    public GameController(IGameService gameService) {
        this.gameService = gameService;
    }

    @PostMapping("syncGamesBySeason")
    public Mono<ResultVO<Void>> syncGamesBySeason(@RequestBody SyncGamesBySeasonReq req) {
        return gameService.syncGamesBySeason(req);
    }

    @PostMapping("syncGamesByDate")
    public Mono<ResultVO<Void>> syncGamesByDate(@RequestBody SyncGamesByDateReq req) {
        return gameService.syncGamesByDate(req);
    }

    @PostMapping("getGamePage")
    public Mono<ResultVO<GetGamesRes>> getGamePage(@RequestBody GetGamesReq req) {
        return gameService.getGamePage(req);
    }

    @PostMapping("getRecentGames")
    public Mono<ResultVO<GetGamesRes>> getRecentGames() {
        return gameService.getRecentGames();
    }

    @PostMapping("getGameCalendar")
    public Mono<ResultVO<GetGameCalendarRes>> getGameCalendar(@RequestBody GetGameCalendarReq req) {
        return gameService.getGameCalendar(req);
    }
}
