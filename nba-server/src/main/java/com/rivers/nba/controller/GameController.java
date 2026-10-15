package com.rivers.nba.controller;

import com.rivers.core.vo.ResultVO;
import com.rivers.nba.GameInfo;
import com.rivers.nba.GetGameCalendarReq;
import com.rivers.nba.GetGameCalendarRes;
import com.rivers.nba.GetGameDetailReq;
import com.rivers.nba.GetGameDetailRes;
import com.rivers.nba.GetGamesReq;
import com.rivers.nba.GetGamesRes;
import com.rivers.nba.ScoreReportReq;
import com.rivers.nba.SyncGamesByDateReq;
import com.rivers.nba.SyncGamesBySeasonReq;
import com.rivers.nba.service.IGameService;
import com.rivers.nba.service.ScorePushService;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
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

    private final ScorePushService scorePushService;

    public GameController(IGameService gameService, ScorePushService scorePushService) {
        this.gameService = gameService;
        this.scorePushService = scorePushService;
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

    /**
     * 比赛详情（每节比分 + 双方球员数据，实时取外部数据源）
     */
    @PostMapping("getGameDetail")
    public Mono<ResultVO<GetGameDetailRes>> getGameDetail(@RequestBody GetGameDetailReq req) {
        return gameService.getGameDetail(req);
    }

    /**
     * 比分实时推送流（SSE 长连接：后台发现比分变动即推送，前台订阅即可，无需轮询）
     */
    @GetMapping(value = "scoreStream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> scoreStream() {
        return scorePushService.stream();
    }

    /**
     * 比分变动上报（更新本地比分并实时广播）
     */
    @PostMapping("reportScoreChange")
    public Mono<ResultVO<GameInfo>> reportScoreChange(@RequestBody ScoreReportReq req) {
        return gameService.reportScoreChange(req);
    }
}
