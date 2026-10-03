package com.rivers.nba.controller;

import com.rivers.core.vo.ResultVO;
import com.rivers.nba.GetDepthChartsReq;
import com.rivers.nba.GetDepthChartsRes;
import com.rivers.nba.service.IDepthChartService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * <p>
 * 阵容深度表 前端控制器
 * </p>
 *
 * @author rivers
 * @since 2026-10-02
 */
@RestController
@RequestMapping("/depthChart")
public class DepthChartController {

    private final IDepthChartService depthChartService;

    public DepthChartController(IDepthChartService depthChartService) {
        this.depthChartService = depthChartService;
    }

    @PostMapping("syncDepthCharts")
    public Mono<ResultVO<Void>> syncDepthCharts() {
        return depthChartService.syncDepthCharts();
    }

    @PostMapping("getDepthChartPage")
    public Mono<ResultVO<GetDepthChartsRes>> getDepthChartPage(@RequestBody GetDepthChartsReq req) {
        return depthChartService.getDepthChartPage(req);
    }
}
