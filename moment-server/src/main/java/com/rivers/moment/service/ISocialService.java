package com.rivers.moment.service;

import com.rivers.core.vo.ResultVO;
import com.rivers.moment.FollowUserReq;
import com.rivers.moment.GetMySocialReq;
import com.rivers.moment.GetMySocialRes;
import com.rivers.moment.JoinCommunityReq;
import com.rivers.moment.LeaveCommunityReq;
import com.rivers.moment.UnfollowUserReq;
import reactor.core.publisher.Mono;

/**
 * <p>
 * 球队社区与用户关注 服务接口
 * </p>
 *
 * @author rivers
 * @since 2026-10-18
 */
public interface ISocialService {

    /**
     * 加入球队社区（登录身份取 loginUser，重复加入幂等）
     */
    Mono<ResultVO<Void>> joinCommunity(JoinCommunityReq req);

    /**
     * 退出球队社区
     */
    Mono<ResultVO<Void>> leaveCommunity(LeaveCommunityReq req);

    /**
     * 我的社交数据：加入的社区、关注的人、粉丝
     */
    Mono<ResultVO<GetMySocialRes>> getMySocial(GetMySocialReq req);

    /**
     * 关注用户（重复关注幂等）
     */
    Mono<ResultVO<Void>> followUser(FollowUserReq req);

    /**
     * 取消关注
     */
    Mono<ResultVO<Void>> unfollowUser(UnfollowUserReq req);
}
