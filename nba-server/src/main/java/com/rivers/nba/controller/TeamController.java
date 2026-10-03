package com.rivers.nba.controller;

import com.rivers.core.vo.ResultVO;
import com.rivers.nba.GetTeamDetailReq;
import com.rivers.nba.GetTeamDetailRes;
import com.rivers.nba.GetTeamsReq;
import com.rivers.nba.GetTeamsRes;
import com.rivers.nba.service.ITeamService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * <p>
 * 球队信息表 前端控制器
 * </p>
 *
 * @author rivers
 * @since 2026-10-02
 */
@RestController
@RequestMapping("/team")
public class TeamController {

    private final ITeamService teamService;

    public TeamController(ITeamService teamService) {
        this.teamService = teamService;
    }

    @PostMapping("syncTeams")
    public Mono<ResultVO<Void>> syncTeams() {
        return teamService.syncTeams();
    }

    @PostMapping("getTeamPage")
    public Mono<ResultVO<GetTeamsRes>> getTeamPage(@RequestBody GetTeamsReq req) {
        return teamService.getTeamPage(req);
    }

    @PostMapping("getTeamDetail")
    public Mono<ResultVO<GetTeamDetailRes>> getTeamDetail(@RequestBody GetTeamDetailReq req) {
        return teamService.getTeamDetail(req);
    }
}
