package com.rivers.im.service.impl;

import com.rivers.core.vo.ResultVO;
import com.rivers.im.entity.TimerGroupMember;
import com.rivers.im.entity.TimerUser;
import com.rivers.im.mapper.TimerGroupMapper;
import com.rivers.im.mapper.TimerGroupMemberMapper;
import com.rivers.im.mapper.TimerUserMapper;
import com.rivers.im.service.IGroupService;
import com.rivers.proto.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class GroupServiceImpl implements IGroupService {

    private final TimerGroupMapper timerGroupMapper;

    private final TimerGroupMemberMapper timerGroupMemberMapper;

    private final TimerUserMapper timerUserMapper;

    public GroupServiceImpl(TimerGroupMapper timerGroupMapper, TimerGroupMemberMapper timerGroupMemberMapper,
                            TimerUserMapper timerUserMapper) {
        this.timerGroupMapper = timerGroupMapper;
        this.timerGroupMemberMapper = timerGroupMemberMapper;
        this.timerUserMapper = timerUserMapper;
    }

    @Override
    public Mono<ResultVO<MyGroupsRes>> getMyGroups(MyGroupsReq myGroupsReq) {
        LoginUser loginUser = myGroupsReq.getLoginUser();
        String userId = loginUser.getUserId();
        return timerGroupMemberMapper.selectGroupIdsByUserId(userId)
                .collectList()
                .flatMap(i -> {
                    if (CollectionUtils.isEmpty(i)) {
                        return Mono.just(ResultVO.ok(MyGroupsRes.newBuilder().build()));
                    }
                    return timerGroupMapper.selectByIds(i)
                            .collectList()
                            .map(j -> {
                                List<GroupDetailRes> list = j.stream()
                                        .map(g ->
                                                GroupDetailRes.newBuilder()
                                                        .setGroupId(g.getId())
                                                        .setGroupName(g.getName())
                                                        .setGroupAvatar(g.getAvatar())
                                                        .setGroupDesc(g.getDescription())
                                                        .setAnnouncement(g.getAnnouncement())
                                                        .setCreateTime(DateTimeFormatter
                                                                .ofPattern("yyyy-MM-dd HH:mm:ss")
                                                                .format(g.getCreateTime()))
                                                        .build())
                                        .toList();
                                return ResultVO.ok(MyGroupsRes.newBuilder()
                                        .addAllGroups(list)
                                        .build());
                            });
                })
                .onErrorResume(e -> {
                    log.error("❌ [Group] 获取我的群组失败: userId={}", userId, e);
                    return Mono.just(ResultVO.fail("获取群组列表失败"));
                });
    }

    @Override
    public Mono<ResultVO<GroupMembersRes>> getGroupMembers(GroupMembersReq groupMembersReq) {
        long groupId = groupMembersReq.getGroupId();
        return timerGroupMemberMapper.selectByGroupId(groupId)
                .collectList()
                .flatMap(i -> {
                    if (CollectionUtils.isEmpty(i)) {
                        return Mono.just(ResultVO.ok(GroupMembersRes.newBuilder().build()));
                    }
                    List<String> userIds = i.stream()
                            .map(TimerGroupMember::getUserId)
                            .distinct()
                            .toList();
                    return timerUserMapper.selectByUserIds(userIds)
                            .collectList()
                            .map(u -> {
                                Map<String, TimerUser> userMap = u.stream()
                                        .collect(Collectors.toMap(
                                                TimerUser::getUserId,
                                                v -> v, (o, _) -> o));
                                List<GroupMemberDetailRes> list = i.stream()
                                        .map(m -> {
                                            TimerUser user = userMap.get(m.getUserId());
                                            return GroupMemberDetailRes.newBuilder()
                                                    .setGroupId(m.getGroupId())
                                                    .setUserId(m.getUserId())
                                                    .setUserName(user != null ? user.getUsername() : "未知")
                                                    .setUserAvatar(user != null ? user.getAvatar() : "")
                                                    .setRole(m.getRole())
                                                    .setJoinAt(DateTimeFormatter
                                                            .ofPattern("yyyy-MM-dd HH:mm:ss")
                                                            .format(m.getJoinedAt()))
                                                    .build();
                                        })
                                        .toList();
                                return ResultVO.ok(GroupMembersRes.newBuilder()
                                        .addAllGroupMembers(list)
                                        .build());
                            });
                })
                .onErrorResume(e -> {
                    log.error("❌ [Group] 获取群成员失败: groupId={}", groupId, e);
                    return Mono.just(ResultVO.fail("获取群成员失败"));
                });
    }
}
