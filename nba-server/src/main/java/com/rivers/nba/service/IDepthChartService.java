package com.rivers.nba.service;

import com.rivers.core.vo.ResultVO;
import com.rivers.nba.GetDepthChartsReq;
import com.rivers.nba.GetDepthChartsRes;
import reactor.core.publisher.Mono;

/**
 * 阵容深度表 服务类
 *
 * @author rivers
 * @since 2026-10-02
 */
public interface IDepthChartService {

    /**
     * 同步阵容深度
     */
    Mono<ResultVO<Void>> syncDepthCharts();

    /**
     * 分页查询阵容深度（teamId 可选过滤）
     */
    Mono<ResultVO<GetDepthChartsRes>> getDepthChartPage(GetDepthChartsReq req);
}
