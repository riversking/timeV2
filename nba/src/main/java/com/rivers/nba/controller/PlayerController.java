package com.rivers.nba.controller;


import com.rivers.core.vo.ResultVO;
import com.rivers.nba.GetPlayersReq;
import com.rivers.nba.GetPlayersRes;
import com.rivers.nba.service.IPlayerService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    public ResultVO<Void> syncAllPlayer() {
        return playerService.syncAllPlayer();
    }

    @PostMapping("getPlayerPage")
    public ResultVO<GetPlayersRes> getPlayerPage(@RequestBody GetPlayersReq getPlayersReq) {
        return playerService.getPlayerPage(getPlayersReq);
    }
}
