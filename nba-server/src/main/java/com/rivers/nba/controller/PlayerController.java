package com.rivers.nba.controller;


import com.rivers.core.vo.ResultVO;
import com.rivers.nba.GetPlayerDetailReq;
import com.rivers.nba.GetPlayerDetailRes;
import com.rivers.nba.GetPlayersReq;
import com.rivers.nba.GetPlayersRes;
import com.rivers.nba.service.IPlayerService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * <p>
 * 球员信息表 前端控制器
 * </p>
 *
 * @author rivers
 * @since 2024-06-16
 */
@RestController
@RequestMapping("/player")
public class PlayerController {

    private final IPlayerService playerService;

    public PlayerController(IPlayerService playerService) {
        this.playerService = playerService;
    }

    @PostMapping("syncAllPlayer")
    public Mono<ResultVO<Void>> syncAllPlayer() {
        return playerService.syncAllPlayer();
    }

    @PostMapping("getPlayerPage")
    public Mono<ResultVO<GetPlayersRes>> getPlayerPage(@RequestBody GetPlayersReq getPlayersReq) {
        return playerService.getPlayerPage(getPlayersReq);
    }

    @PostMapping("getPlayerDetail")
    public Mono<ResultVO<GetPlayerDetailRes>> getPlayerDetail(@RequestBody GetPlayerDetailReq req) {
        return playerService.getPlayerDetail(req);
    }
}
