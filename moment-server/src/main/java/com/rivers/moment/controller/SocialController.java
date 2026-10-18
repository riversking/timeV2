package com.rivers.moment.controller;

import com.rivers.core.vo.ResultVO;
import com.rivers.moment.FollowUserReq;
import com.rivers.moment.GetMySocialReq;
import com.rivers.moment.GetMySocialRes;
import com.rivers.moment.JoinCommunityReq;
import com.rivers.moment.LeaveCommunityReq;
import com.rivers.moment.UnfollowUserReq;
import com.rivers.moment.service.ISocialService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * <p>
 * 球队社区与用户关注 前端控制器
 * </p>
 *
 * @author rivers
 * @since 2026-10-18
 */
@RestController
@RequestMapping("moment")
public class SocialController {

    private final ISocialService socialService;

    public SocialController(ISocialService socialService) {
        this.socialService = socialService;
    }

    /**
     * 加入球队社区
     */
    @PostMapping("joinCommunity")
    public Mono<ResultVO<Void>> joinCommunity(@RequestBody JoinCommunityReq req) {
        if (!hasLoginUser(req.hasLoginUser(), req.getLoginUser().getUserId())) {
            return Mono.just(ResultVO.fail("未获取到登录身份"));
        }
        return socialService.joinCommunity(req);
    }

    /**
     * 退出球队社区
     */
    @PostMapping("leaveCommunity")
    public Mono<ResultVO<Void>> leaveCommunity(@RequestBody LeaveCommunityReq req) {
        if (!hasLoginUser(req.hasLoginUser(), req.getLoginUser().getUserId())) {
            return Mono.just(ResultVO.fail("未获取到登录身份"));
        }
        return socialService.leaveCommunity(req);
    }

    /**
     * 我的社区 / 关注 / 粉丝
     */
    @PostMapping("getMySocial")
    public Mono<ResultVO<GetMySocialRes>> getMySocial(@RequestBody GetMySocialReq req) {
        if (!hasLoginUser(req.hasLoginUser(), req.getLoginUser().getUserId())) {
            return Mono.just(ResultVO.fail("未获取到登录身份"));
        }
        return socialService.getMySocial(req);
    }

    /**
     * 关注用户
     */
    @PostMapping("followUser")
    public Mono<ResultVO<Void>> followUser(@RequestBody FollowUserReq req) {
        if (!hasLoginUser(req.hasLoginUser(), req.getLoginUser().getUserId())) {
            return Mono.just(ResultVO.fail("未获取到登录身份"));
        }
        return socialService.followUser(req);
    }

    /**
     * 取消关注
     */
    @PostMapping("unfollowUser")
    public Mono<ResultVO<Void>> unfollowUser(@RequestBody UnfollowUserReq req) {
        if (!hasLoginUser(req.hasLoginUser(), req.getLoginUser().getUserId())) {
            return Mono.just(ResultVO.fail("未获取到登录身份"));
        }
        return socialService.unfollowUser(req);
    }

    private static boolean hasLoginUser(boolean present, String userId) {
        return present && StringUtils.isNotBlank(userId);
    }
}
