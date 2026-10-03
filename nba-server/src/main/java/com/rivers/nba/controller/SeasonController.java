package com.rivers.nba.controller;

import com.rivers.core.vo.ResultVO;
import com.rivers.nba.GetCurrentSeasonRes;
import com.rivers.nba.service.ICurrentSeasonService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * <p>
 * 当前赛季信息表 前端控制器
 * </p>
 *
 * @author rivers
 * @since 2026-10-02
 */
@RestController
@RequestMapping("/season")
public class SeasonController {

    private final ICurrentSeasonService currentSeasonService;

    public SeasonController(ICurrentSeasonService currentSeasonService) {
        this.currentSeasonService = currentSeasonService;
    }

    @PostMapping("syncCurrentSeason")
    public Mono<ResultVO<Void>> syncCurrentSeason() {
        return currentSeasonService.syncCurrentSeason();
    }

    @PostMapping("getCurrentSeason")
    public Mono<ResultVO<GetCurrentSeasonRes>> getCurrentSeason() {
        return currentSeasonService.getCurrentSeason();
    }
}
