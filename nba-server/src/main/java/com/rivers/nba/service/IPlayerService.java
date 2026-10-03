package com.rivers.nba.service;

import com.rivers.core.vo.ResultVO;
import com.rivers.nba.GetPlayerDetailReq;
import com.rivers.nba.GetPlayerDetailRes;
import com.rivers.nba.GetPlayersReq;
import com.rivers.nba.GetPlayersRes;
import reactor.core.publisher.Mono;

/**
 * <p>
 * 球员信息表 服务类
 * </p>
 *
 * @author rivers
 * @since 2024-06-16
 */
public interface IPlayerService {

    Mono<ResultVO<Void>> syncAllPlayer();


    Mono<ResultVO<GetPlayersRes>> getPlayerPage(GetPlayersReq getPlayersReq);

    Mono<ResultVO<GetPlayerDetailRes>> getPlayerDetail(GetPlayerDetailReq req);
}
