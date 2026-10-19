package com.rivers.moment.controller;

import com.rivers.core.vo.ResultVO;
import com.rivers.moment.*;
import com.rivers.moment.service.ISocialService;
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
        return socialService.joinCommunity(req);
    }

    /**
     * 退出球队社区
     */
    @PostMapping("leaveCommunity")
    public Mono<ResultVO<Void>> leaveCommunity(@RequestBody LeaveCommunityReq req) {
        return socialService.leaveCommunity(req);
    }

    /**
     * 我的社区 / 关注 / 粉丝
     */
    @PostMapping("getMySocial")
    public Mono<ResultVO<GetMySocialRes>> getMySocial(@RequestBody GetMySocialReq req) {
        return socialService.getMySocial(req);
    }

    /**
     * 关注用户
     */
    @PostMapping("followUser")
    public Mono<ResultVO<Void>> followUser(@RequestBody FollowUserReq req) {
        return socialService.followUser(req);
    }

    /**
     * 取消关注
     */
    @PostMapping("unfollowUser")
    public Mono<ResultVO<Void>> unfollowUser(@RequestBody UnfollowUserReq req) {
        return socialService.unfollowUser(req);
    }
}
