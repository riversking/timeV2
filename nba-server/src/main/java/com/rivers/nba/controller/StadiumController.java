package com.rivers.nba.controller;

import com.rivers.core.vo.ResultVO;
import com.rivers.nba.GetStadiumDetailReq;
import com.rivers.nba.GetStadiumDetailRes;
import com.rivers.nba.GetStadiumsReq;
import com.rivers.nba.GetStadiumsRes;
import com.rivers.nba.service.IStadiumService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * <p>
 * 球馆信息表 前端控制器
 * </p>
 *
 * @author rivers
 * @since 2026-10-02
 */
@RestController
@RequestMapping("/stadium")
public class StadiumController {

    private final IStadiumService stadiumService;

    public StadiumController(IStadiumService stadiumService) {
        this.stadiumService = stadiumService;
    }

    @PostMapping("syncStadiums")
    public Mono<ResultVO<Void>> syncStadiums() {
        return stadiumService.syncStadiums();
    }

    @PostMapping("getStadiumPage")
    public Mono<ResultVO<GetStadiumsRes>> getStadiumPage(@RequestBody GetStadiumsReq req) {
        return stadiumService.getStadiumPage(req);
    }

    @PostMapping("getStadiumDetail")
    public Mono<ResultVO<GetStadiumDetailRes>> getStadiumDetail(@RequestBody GetStadiumDetailReq req) {
        return stadiumService.getStadiumDetail(req);
    }
}
