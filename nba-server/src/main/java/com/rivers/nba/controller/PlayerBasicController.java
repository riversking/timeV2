package com.rivers.nba.controller;

import com.rivers.core.vo.ResultVO;
import com.rivers.nba.GetPlayerBasicsReq;
import com.rivers.nba.GetPlayerBasicsRes;
import com.rivers.nba.SyncPlayersByTeamReq;
import com.rivers.nba.service.IPlayerBasicService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * <p>
 * 球员简档信息表 前端控制器
 * </p>
 *
 * @author rivers
 * @since 2026-10-02
 */
@RestController
@RequestMapping("/playerBasic")
public class PlayerBasicController {

    private final IPlayerBasicService playerBasicService;

    public PlayerBasicController(IPlayerBasicService playerBasicService) {
        this.playerBasicService = playerBasicService;
    }

    @PostMapping("syncActivePlayers")
    public Mono<ResultVO<Void>> syncActivePlayers() {
        return playerBasicService.syncActivePlayers();
    }

    @PostMapping("syncFreeAgents")
    public Mono<ResultVO<Void>> syncFreeAgents() {
        return playerBasicService.syncFreeAgents();
    }

    @PostMapping("syncPlayersByTeam")
    public Mono<ResultVO<Void>> syncPlayersByTeam(@RequestBody SyncPlayersByTeamReq req) {
        return playerBasicService.syncPlayersByTeam(req);
    }

    @PostMapping("syncFreeAgentsFull")
    public Mono<ResultVO<Void>> syncFreeAgentsFull() {
        return playerBasicService.syncFreeAgentsFull();
    }

    @PostMapping("getPlayerBasicPage")
    public Mono<ResultVO<GetPlayerBasicsRes>> getPlayerBasicPage(@RequestBody GetPlayerBasicsReq req) {
        return playerBasicService.getPlayerBasicPage(req);
    }
}
