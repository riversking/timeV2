package com.rivers.moment.service.impl;

import com.rivers.core.vo.ResultVO;
import com.rivers.moment.FollowUserReq;
import com.rivers.moment.GetMySocialReq;
import com.rivers.moment.GetMySocialRes;
import com.rivers.moment.JoinCommunityReq;
import com.rivers.moment.LeaveCommunityReq;
import com.rivers.moment.SocialUser;
import com.rivers.moment.UnfollowUserReq;
import com.rivers.moment.entity.UserFollow;
import com.rivers.moment.repository.CommunityMemberRepository;
import com.rivers.moment.repository.UserFollowRepository;
import com.rivers.moment.service.ISocialService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * <p>
 * 球队社区与用户关注 服务实现类
 * </p>
 *
 * @author rivers
 * @since 2026-10-18
 */
@Slf4j
@Service
public class SocialServiceImpl implements ISocialService {

    private final CommunityMemberRepository communityMemberRepository;
    private final UserFollowRepository userFollowRepository;

    public SocialServiceImpl(CommunityMemberRepository communityMemberRepository,
                             UserFollowRepository userFollowRepository) {
        this.communityMemberRepository = communityMemberRepository;
        this.userFollowRepository = userFollowRepository;
    }

    @Override
    public Mono<ResultVO<Void>> joinCommunity(JoinCommunityReq req) {
        int teamId = req.getTeamId();
        if (teamId <= 0) {
            return Mono.just(ResultVO.fail("球队ID不能为空"));
        }
        String userId = req.getLoginUser().getUserId();
        String userName = defaultString(req.getLoginUser().getUsername());
        return complete(communityMemberRepository.upsert(teamId, userId, userName), "加入社区失败");
    }

    @Override
    public Mono<ResultVO<Void>> leaveCommunity(LeaveCommunityReq req) {
        int teamId = req.getTeamId();
        if (teamId <= 0) {
            return Mono.just(ResultVO.fail("球队ID不能为空"));
        }
        String userId = req.getLoginUser().getUserId();
        return complete(communityMemberRepository.leave(teamId, userId), "退出社区失败");
    }

    @Override
    public Mono<ResultVO<GetMySocialRes>> getMySocial(GetMySocialReq req) {
        String userId = req.getLoginUser().getUserId();
        Mono<List<Integer>> teamIds = communityMemberRepository.findTeamIdsByUserId(userId).collectList();
        Mono<List<UserFollow>> following = userFollowRepository.findFollowing(userId).collectList();
        Mono<List<UserFollow>> followers = userFollowRepository.findFollowers(userId).collectList();
        return Mono.zip(teamIds, following, followers)
                .map(tuple -> ResultVO.ok(GetMySocialRes.newBuilder()
                        .addAllCommunityTeamIds(tuple.getT1())
                        .addAllFollowing(tuple.getT2().stream()
                                .map(item -> socialUser(item.getFolloweeId(), item.getFolloweeName()))
                                .toList())
                        .addAllFollowers(tuple.getT3().stream()
                                .map(item -> socialUser(item.getFollowerId(), item.getFollowerName()))
                                .toList())
                        .build()))
                .onErrorResume(e -> {
                    log.error("获取社交数据失败: userId={}, {}", userId, e.getMessage());
                    return Mono.just(ResultVO.fail("获取社交数据失败"));
                });
    }

    @Override
    public Mono<ResultVO<Void>> followUser(FollowUserReq req) {
        String followerId = req.getLoginUser().getUserId();
        String targetUserId = StringUtils.trimToEmpty(req.getTargetUserId());
        if (targetUserId.isEmpty()) {
            return Mono.just(ResultVO.fail("被关注用户不能为空"));
        }
        if (targetUserId.equals(followerId)) {
            return Mono.just(ResultVO.fail("不能关注自己"));
        }
        String followerName = defaultString(req.getLoginUser().getUsername());
        String targetUserName = StringUtils.defaultIfBlank(
                StringUtils.trimToEmpty(req.getTargetUserName()), targetUserId);
        return complete(userFollowRepository.upsert(
                followerId, followerName, targetUserId, targetUserName), "关注失败");
    }

    @Override
    public Mono<ResultVO<Void>> unfollowUser(UnfollowUserReq req) {
        String followerId = req.getLoginUser().getUserId();
        String targetUserId = StringUtils.trimToEmpty(req.getTargetUserId());
        if (targetUserId.isEmpty()) {
            return Mono.just(ResultVO.fail("被关注用户不能为空"));
        }
        return complete(userFollowRepository.unfollow(followerId, targetUserId), "取消关注失败");
    }

    private static SocialUser socialUser(String userId, String userName) {
        return SocialUser.newBuilder()
                .setUserId(defaultString(userId))
                .setUserName(defaultString(userName))
                .build();
    }

    private static Mono<ResultVO<Void>> complete(Mono<Long> action, String failMessage) {
        return action.thenReturn(ResultVO.<Void>ok())
                .onErrorResume(e -> {
                    log.error("{}: {}", failMessage, e.getMessage());
                    return Mono.just(ResultVO.fail(failMessage));
                });
    }

    private static String defaultString(String value) {
        return value == null ? "" : value;
    }
}
