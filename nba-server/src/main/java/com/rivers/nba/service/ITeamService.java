package com.rivers.nba.service;

import com.rivers.core.vo.ResultVO;
import com.rivers.nba.GetTeamDetailReq;
import com.rivers.nba.GetTeamDetailRes;
import com.rivers.nba.GetTeamsReq;
import com.rivers.nba.GetTeamsRes;
import reactor.core.publisher.Mono;

/**
 * 球队信息表 服务类
 *
 * @author rivers
 * @since 2026-10-02
 */
public interface ITeamService {

    /**
     * 同步球队（拉取外部数据，逻辑删除旧数据后批量插入）
     */
    Mono<ResultVO<Void>> syncTeams();

    /**
     * 分页查询球队
     */
    Mono<ResultVO<GetTeamsRes>> getTeamPage(GetTeamsReq req);

    /**
     * 查询球队详情
     */
    Mono<ResultVO<GetTeamDetailRes>> getTeamDetail(GetTeamDetailReq req);
}
