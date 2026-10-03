package com.rivers.nba.service;

import com.rivers.core.vo.ResultVO;
import com.rivers.nba.GetNewsReq;
import com.rivers.nba.GetNewsRes;
import reactor.core.publisher.Mono;

/**
 * NBA 新闻资讯 服务类
 *
 * @author rivers
 * @since 2026-10-03
 */
public interface INewsService {

    /**
     * 查询 NBA 最新新闻（数据源 ESPN 公开接口）
     */
    Mono<ResultVO<GetNewsRes>> getNews(GetNewsReq req);
}
