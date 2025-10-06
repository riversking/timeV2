package com.rivers.nba.service;

import com.rivers.core.vo.ResultVO;
import com.rivers.nba.GetPlayersReq;
import com.rivers.nba.GetPlayersRes;

/**
 * <p>
 * 球员信息表 服务类
 * </p>
 *
 * @author rivers
 * @since 2024-06-16
 */
public interface IPlayerService {

    ResultVO<ResultVO.EmptyType> syncAllPlayer();


    ResultVO<GetPlayersRes> getPlayerPage(GetPlayersReq getPlayersReq);
}
