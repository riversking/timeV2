package com.rivers.nba.service;

import com.rivers.core.vo.ResultVO;
import com.rivers.nba.GetStadiumDetailReq;
import com.rivers.nba.GetStadiumDetailRes;
import com.rivers.nba.GetStadiumsReq;
import com.rivers.nba.GetStadiumsRes;
import reactor.core.publisher.Mono;

/**
 * 球馆信息表 服务类
 *
 * @author rivers
 * @since 2026-10-02
 */
public interface IStadiumService {

    /**
     * 同步球馆（拉取外部数据，逻辑删除旧数据后批量插入）
     */
    Mono<ResultVO<Void>> syncStadiums();

    /**
     * 分页查询球馆
     */
    Mono<ResultVO<GetStadiumsRes>> getStadiumPage(GetStadiumsReq req);

    /**
     * 查询球馆详情
     */
    Mono<ResultVO<GetStadiumDetailRes>> getStadiumDetail(GetStadiumDetailReq req);
}
