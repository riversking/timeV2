package com.rivers.im.router.handler;

import com.rivers.im.entity.TimerGroup;
import com.rivers.im.entity.TimerGroupMember;
import com.rivers.im.mapper.TimerGroupMapper;
import com.rivers.im.mapper.TimerGroupMemberMapper;
import com.rivers.im.router.TopicHandler;
import com.rivers.im.service.IWebSocketPushService;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.LocalDateTime;

/**
 * group topic handler
 *
 */
@Component
@Slf4j
@NullMarked
public class GroupTopicHandler implements TopicHandler {

    private static final String ACTION = "action";
    private static final String GROUP_NOTIFY = "notification";
    private static final byte ROLE_MEMBER = 1;
    private static final byte ROLE_ADMIN = 2;
    private static final byte ROLE_OWNER = 3;
    private static final int DEFAULT_MAX_MEMBERS = 200;
    private static final String GROUP_ID = "groupId";
    private static final String USER_IDS = "userIds";
    private static final String GROUP_ANNOUNCE = "group_announce";
    private static final String GROUP_INVITE = "group_invite";
    private static final String GROUP_KICK = "group_kick";
    private static final String GROUP_DISMISS = "group_dismiss";
    private static final String NO_GROUP_ID = "缺少群组ID";
    private static final String GROUP_LEAVE = "group_leave";
    private static final String GROUP_CREATE = "group_create";

    private final TimerGroupMapper timerGroupMapper;
    private final TimerGroupMemberMapper timerGroupMemberMapper;
    private final IWebSocketPushService webSocketPushService;
    private final ObjectMapper objectMapper;

    public GroupTopicHandler(TimerGroupMapper timerGroupMapper, TimerGroupMemberMapper timerGroupMemberMapper,
                             IWebSocketPushService webSocketPushService, ObjectMapper objectMapper) {
        this.timerGroupMapper = timerGroupMapper;
        this.timerGroupMemberMapper = timerGroupMemberMapper;
        this.webSocketPushService = webSocketPushService;
        this.objectMapper = objectMapper;
    }


    @Override
    public String getTopic() {
        return "group";
    }

    @Override
    public Mono<Void> handleInbound(String userId, String connId, JsonNode payload) {
        String action = payload.path(ACTION).asString("");
        return switch (action) {
            case "create" -> handleCreate(userId, payload);
            case "leave" -> handleLeave(userId, payload);
            case "dismiss" -> handleDismiss(userId, payload);
            case "kick" -> handleKick(userId, payload);
            case "invite" -> handleInvite(userId, payload);
            case "announce" -> handleAnnounce(userId, payload);
            default -> {
                log.warn("👥 [Group] 未知操作: action={}, userId={}", action, userId);
                yield Mono.empty();
            }
        };
    }


    /**
     * 创建群组（静默模式）
     * 拉入选中的好友但不通知他们 —— 首条消息发出后好友才能看到群
     */
    private Mono<Void> handleCreate(String userId, JsonNode payload) {
        String name = payload.path("name").asString("");
        if (name.isEmpty()) {
            log.warn("👥 [Group] 群名称为空: userId={}", userId);
            return sendResult(userId, GROUP_CREATE, false, "群名称不能为空");
        }
        String description = payload.path("description").asString("");
        String avatar = payload.path("avatar").asString("");
        JsonNode userIdsNode = payload.path(USER_IDS);
        TimerGroup group = new TimerGroup();
        group.setName(name);
        group.setAvatar(avatar);
        group.setDescription(description);
        group.setMaxMembers(DEFAULT_MAX_MEMBERS);
        group.setCreateUser(userId);
        group.setUpdateUser(userId);
        return timerGroupMapper.save(group)
                .flatMap(savedGroup -> {
                    TimerGroupMember owner = newMember(savedGroup.getId(), userId, ROLE_OWNER, userId);
                    Flux<TimerGroupMember> friendMembers = extractUserIds(userIdsNode, userId)
                            .map(id -> newMember(savedGroup.getId(), id, ROLE_MEMBER, userId));
                    return friendMembers.collectList()
                            .flatMapMany(friends -> {
                                friends.addFirst(owner);
                                return timerGroupMemberMapper.saveAll(friends);
                            })
                            .then(Mono.fromCallable(() -> {
                                ArrayNode memberArr = objectMapper.createArrayNode();
                                extractUserIds(userIdsNode, userId)
                                        .subscribe(memberArr::add);
                                return memberArr;
                            }))
                            .flatMap(memberArr -> pushToUser(userId, "group_created",
                                    objectMapper.createObjectNode()
                                            .put(GROUP_ID, savedGroup.getId())
                                            .put("name", savedGroup.getName())
                                            .put("avatar", savedGroup.getAvatar() != null ?
                                                    savedGroup.getAvatar() : "")
                                            .put("description", savedGroup.getDescription() != null ?
                                                    savedGroup.getDescription() : "")
                                            .set("members", memberArr)));
                })
                .then(sendResult(userId, GROUP_CREATE, true, "群组创建成功"))
                .doOnSuccess(v -> log.info("👥 [Group] 群组创建成功(静默): name={}, creator={}", name, userId))
                .onErrorResume(e -> {
                    log.error("❌ [Group] 创建群组失败: userId={}", userId, e);
                    return sendResult(userId, GROUP_CREATE, false, "创建群组失败");
                });
    }

    /**
     * 退出群组 —— 群主不能直接退出，需先转让或解散
     */
    private Mono<Void> handleLeave(String userId, JsonNode payload) {
        long groupId = payload.path(GROUP_ID).asLong(0);
        if (groupId == 0) {
            log.warn("👥 [Group] 退出群组缺少 groupId: userId={}", userId);
            return sendResult(userId, GROUP_LEAVE, false, NO_GROUP_ID);
        }
        return timerGroupMemberMapper.selectByGroupIdAndUserId(groupId, userId)
                .flatMap(member -> {
                    if (member.getRole() == ROLE_OWNER) {
                        log.warn("👥 [Group] 群主不能直接退出: userId={}, groupId={}", userId, groupId);
                        return sendResult(userId, GROUP_LEAVE, false,
                                "群主不能直接退出，请先转让或解散群组");
                    }
                    return timerGroupMemberMapper.deleteMember(groupId, userId, userId)
                            .then(pushToUser(userId, "group_left", buildSimplePayload(groupId)))
                            .then(sendResult(userId, GROUP_LEAVE, true, "已退出群组"));
                })
                .onErrorResume(e -> {
                    log.error("❌ [Group] 退出群组失败: userId={}, groupId={}", userId, groupId, e);
                    return sendResult(userId, GROUP_LEAVE, false, "退出群组失败");
                });
    }

    /**
     * 解散群组
     *
     */
    private Mono<Void> handleDismiss(String userId, JsonNode payload) {
        long groupId = payload.path(GROUP_ID).asLong(0);
        if (groupId == 0) {
            log.warn("👥 [Group] 解散群组缺少 groupId: userId={}", userId);
            return sendResult(userId, GROUP_DISMISS, false, NO_GROUP_ID);
        }
        return timerGroupMemberMapper.selectByGroupIdAndUserId(groupId, userId)
                .flatMap(member -> {
                    if (member.getRole() != ROLE_OWNER) {
                        log.warn("👥 [Group] 非群主不能解散: userId={}, groupId={}", userId, groupId);
                        return sendResult(userId, GROUP_DISMISS, false, "只有群主可以解散群组");
                    }
                    ObjectNode notifyData = buildSimplePayload(groupId);
                    return pushToGroup(groupId, "group_dismissed", notifyData)
                            .then(timerGroupMemberMapper.deleteAllMembers(groupId, userId))
                            .then(timerGroupMapper.findById(groupId)
                                    .flatMap(g -> {
                                        g.setIsDeleted(1);
                                        g.setUpdateUser(userId);
                                        return timerGroupMapper.save(g).then();
                                    }))
                            .then(sendResult(userId, GROUP_DISMISS, true, "群组已解散"));
                })
                .onErrorResume(e -> {
                    log.error("❌ [Group] 解散群组失败: userId={}, groupId={}", userId, groupId, e);
                    return sendResult(userId, GROUP_DISMISS, false, "解散群组失败");
                });
    }

    /**
     * 踢人
     *
     */
    private Mono<Void> handleKick(String userId, JsonNode payload) {
        long groupId = payload.path(GROUP_ID).asLong(0);
        String targetUserId = payload.path("targetUserId").asString("");
        if (groupId == 0 || targetUserId.isEmpty()) {
            log.warn("👥 [Group] 踢人参数不全: userId={}", userId);
            return sendResult(userId, GROUP_KICK, false, "参数不全");
        }
        if (targetUserId.equals(userId)) {
            log.warn("👥 [Group] 不能踢自己: userId={}", userId);
            return sendResult(userId, GROUP_KICK, false, "不能踢自己");
        }
        return timerGroupMemberMapper.selectByGroupIdAndUserId(groupId, userId)
                .flatMap(operator -> {
                    if (operator.getRole() < ROLE_ADMIN) {
                        log.warn("👥 [Group] 无权限踢人: userId={}, role={}", userId, operator.getRole());
                        return sendResult(userId, GROUP_KICK, false, "无权限踢人");
                    }
                    return timerGroupMemberMapper.selectByGroupIdAndUserId(groupId, targetUserId)
                            .flatMap(target -> {
                                if (target.getRole() >= operator.getRole()) {
                                    log.warn("👥 [Group] 不能踢同级或更高权限: op={}, target={}",
                                            userId, targetUserId);
                                    return sendResult(userId, GROUP_KICK, false,
                                            "不能踢出同级或更高权限成员");
                                }
                                return timerGroupMemberMapper.deleteMember(groupId, targetUserId, userId)
                                        .then(pushToUser(targetUserId, "kicked_from_group",
                                                buildSimplePayload(groupId)))
                                        .then(sendResult(userId, GROUP_KICK, true, "已踢出成员"));
                            });
                })
                .onErrorResume(e -> {
                    log.error("❌ [Group] 踢人失败: userId={}, groupId={}", userId, groupId, e);
                    return sendResult(userId, GROUP_KICK, false, "踢人失败");
                });
    }

    /**
     * 邀请
     *
     */
    private Mono<Void> handleInvite(String userId, JsonNode payload) {
        long groupId = payload.path(GROUP_ID).asLong(0);
        JsonNode userIdsNode = payload.path(USER_IDS);
        if (groupId == 0 || !userIdsNode.isArray() || userIdsNode.isEmpty()) {
            log.warn("👥 [Group] 邀请参数不全: userId={}", userId);
            return sendResult(userId, GROUP_INVITE, false, "参数不全");
        }
        return timerGroupMemberMapper.selectByGroupIdAndUserId(groupId, userId)
                .flatMap(member -> timerGroupMemberMapper.selectMembersCount(groupId)
                        .flatMap(count -> timerGroupMapper.findById(groupId)
                                .flatMap(group -> {
                                    int remaining = group.getMaxMembers() - count;
                                    return extractUserIds(userIdsNode, null)
                                            .take(remaining)
                                            .concatMap(targetUserId ->
                                                    timerGroupMemberMapper
                                                            .selectByGroupIdAndUserId(groupId, targetUserId)
                                                            .flatMap(_ -> {
                                                                log.debug("👥 [Group] 用户已在群中: {}", targetUserId);
                                                                return Mono.empty();
                                                            })
                                                            .switchIfEmpty(Mono.defer(() -> {
                                                                TimerGroupMember newM = newMember(
                                                                        groupId, targetUserId,
                                                                        ROLE_MEMBER, userId);
                                                                return timerGroupMemberMapper.save(newM)
                                                                        .thenReturn(targetUserId);
                                                            }))
                                            )
                                            .collectList()
                                            .flatMap(joined -> {
                                                if (joined.isEmpty()) {
                                                    return sendResult(userId, GROUP_INVITE,
                                                            false, "所选用户已在群中");
                                                }
                                                return pushToUser(userId, "members_invited",
                                                        objectMapper.createObjectNode()
                                                                .put(GROUP_ID, groupId)
                                                                .set(USER_IDS,
                                                                        objectMapper.valueToTree(joined)))
                                                        .then(sendResult(userId, GROUP_INVITE,
                                                                true, "邀请已发送"));
                                            });
                                })))
                .onErrorResume(e -> {
                    log.error("❌ [Group] 邀请失败: userId={}, groupId={}", userId, groupId, e);
                    return sendResult(userId, GROUP_INVITE, false, "邀请失败");
                });
    }

    /**
     * 公告
     *
     */
    private Mono<Void> handleAnnounce(String userId, JsonNode payload) {
        long groupId = payload.path(GROUP_ID).asLong(0);
        String announcement = payload.path("announcement").asString("");
        if (groupId == 0) {
            log.warn("👥 [Group] 发布公告缺少 groupId: userId={}", userId);
            return sendResult(userId, GROUP_ANNOUNCE, false, NO_GROUP_ID);
        }
        return timerGroupMemberMapper.selectByGroupIdAndUserId(groupId, userId)
                .flatMap(member -> {
                    if (member.getRole() < ROLE_ADMIN) {
                        log.warn("👥 [Group] 无权限发公告: userId={}, role={}", userId, member.getRole());
                        return sendResult(userId, GROUP_ANNOUNCE, false, "无权限发布公告");
                    }
                    return timerGroupMapper.updateAnnouncement(groupId, announcement, userId)
                            .then(pushToGroup(groupId, GROUP_ANNOUNCE,
                                    objectMapper.createObjectNode()
                                            .put(GROUP_ID, groupId)
                                            .put("announcement", announcement)))
                            .then(sendResult(userId, GROUP_ANNOUNCE, true, "公告已发布"));
                })
                .onErrorResume(e -> {
                    log.error("❌ [Group] 发布公告失败: userId={}, groupId={}", userId, groupId, e);
                    return sendResult(userId, GROUP_ANNOUNCE, false, "发布公告失败");
                });
    }

    // ==================== 推送辅助方法 ====================
    private Flux<String> extractUserIds(JsonNode node, @Nullable String excludeUserId) {
        if (!node.isArray()) {
            return Flux.empty();
        }
        ArrayNode arr = (ArrayNode) node;
        return Flux.fromIterable(arr)
                .map(n -> n.asString(""))
                .filter(id -> !id.isEmpty())
                .filter(id -> !id.equals(excludeUserId))
                .distinct();
    }

    private Mono<Void> pushToUser(String userId, String action, ObjectNode data) {
        data.put(ACTION, action)
                .put("ts", System.currentTimeMillis());
        return webSocketPushService.pushToUser(userId, GROUP_NOTIFY, data)
                .doOnSuccess(v -> log.debug("👥 [Group] 推送: to={}, action={}", userId, action))
                .onErrorResume(e -> {
                    log.error("📭 pushToUser 用户离线: to={}, action={}", userId, action, e);
                    return Mono.empty();
                });
    }

    private Mono<Void> pushToGroup(long groupId, String action, ObjectNode data) {
        data.put(ACTION, action).put("ts", System.currentTimeMillis());
        return timerGroupMemberMapper.selectByGroupId(groupId)
                .flatMap(member ->
                        webSocketPushService.pushToUser(member.getUserId(), GROUP_NOTIFY, data)
                                .onErrorResume(e -> Mono.empty()))
                .then();
    }

    private ObjectNode buildSimplePayload(long groupId) {
        return objectMapper.createObjectNode().put(GROUP_ID, groupId);
    }

    private TimerGroupMember newMember(long groupId, String userId,
                                       int role, String operator) {
        LocalDateTime now = LocalDateTime.now();
        TimerGroupMember member = new TimerGroupMember();
        member.setGroupId(groupId);
        member.setUserId(userId);
        member.setRole(role);
        member.setJoinedAt(now);
        member.setCreateUser(operator);
        member.setUpdateUser(operator);
        return member;
    }

    /**
     * 向前端推送操作结果（成功或失败）
     */
    private Mono<Void> sendResult(String userId, String operation, boolean success, String message) {
        ObjectNode data = objectMapper.createObjectNode()
                .put(ACTION, operation + "_result")
                .put("success", success)
                .put("message", message)
                .put("ts", System.currentTimeMillis());
        return webSocketPushService.pushToUser(userId, GROUP_NOTIFY, data)
                .doOnSuccess(v -> log.debug("👥 [Group] 操作结果已推送: to={}, op={}, success={}",
                        userId, operation, success))
                .onErrorResume(e -> {
                    log.warn("⚠️ 推送操作结果失败: to={}, op={}", userId, operation, e);
                    return Mono.empty();
                });
    }
}