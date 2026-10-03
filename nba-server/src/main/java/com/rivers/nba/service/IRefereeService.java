package com.rivers.nba.service;

import com.rivers.core.vo.ResultVO;
import com.rivers.nba.GetRefereesReq;
import com.rivers.nba.GetRefereesRes;
import reactor.core.publisher.Mono;

/**
 * 裁判信息表 服务类
 *
 * @author rivers
 * @since 2026-10-02
 */
public interface IRefereeService {

    /**
     * 同步裁判
     */
    Mono<ResultVO<Void>> syncReferees();

    /**
     * 分页查询裁判
     */
    Mono<ResultVO<GetRefereesRes>> getRefereePage(GetRefereesReq req);
}
