package com.rivers.nba.controller;

import com.rivers.core.vo.ResultVO;
import com.rivers.nba.GetRefereesReq;
import com.rivers.nba.GetRefereesRes;
import com.rivers.nba.service.IRefereeService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * <p>
 * 裁判信息表 前端控制器
 * </p>
 *
 * @author rivers
 * @since 2026-10-02
 */
@RestController
@RequestMapping("/referee")
public class RefereeController {

    private final IRefereeService refereeService;

    public RefereeController(IRefereeService refereeService) {
        this.refereeService = refereeService;
    }

    @PostMapping("syncReferees")
    public Mono<ResultVO<Void>> syncReferees() {
        return refereeService.syncReferees();
    }

    @PostMapping("getRefereePage")
    public Mono<ResultVO<GetRefereesRes>> getRefereePage(@RequestBody GetRefereesReq req) {
        return refereeService.getRefereePage(req);
    }
}
