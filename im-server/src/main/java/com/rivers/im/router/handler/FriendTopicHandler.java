package com.rivers.im.router.handler;

import com.rivers.im.entity.TimerFriend;
import com.rivers.im.entity.TimerFriendRequest;
import com.rivers.im.entity.TimerMessage;
import com.rivers.im.mapper.TimerFriendMapper;
import com.rivers.im.mapper.TimerFriendRequestMapper;
import com.rivers.im.mapper.TimerMessageMapper;
import com.rivers.im.router.TopicHandler;
import com.rivers.im.service.IWebSocketPushService;
import com.rivers.im.util.SnowflakeIdGenerator;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.LocalDateTime;
import java.util.Optional;

@Component
@Slf4j
@NullMarked
public class FriendTopicHandler implements TopicHandler {

    private static final String ACTION = "action";
    private static final String NOTIFICATION = "notification";
    private static final String REQUEST_ID = "requestId";
    /**
     * 系统通知消息类型
     */
    private static final int MSG_TYPE_NOTIFY = 6;
    private static final String FRIEND_REJECT = "friend_reject";
    private static final String FRIEND_ACCEPT = "friend_accept";
    private static final String FRIEND_REQUEST = "friend_request";

    private final TimerFriendRequestMapper timerFriendRequestMapper;
    private final TimerFriendMapper timerFriendMapper;
    private final TimerMessageMapper timerMessageMapper;
    private final IWebSocketPushService webSocketPushService;
    private final ObjectMapper objectMapper;
    private final SnowflakeIdGenerator snowflakeIdGenerator;

    public FriendTopicHandler(TimerFriendRequestMapper timerFriendRequestMapper, TimerFriendMapper timerFriendMapper,
                              TimerMessageMapper timerMessageMapper, IWebSocketPushService webSocketPushService,
                              ObjectMapper objectMapper, SnowflakeIdGenerator snowflakeIdGenerator) {
        this.timerFriendRequestMapper = timerFriendRequestMapper;
        this.timerFriendMapper = timerFriendMapper;
        this.timerMessageMapper = timerMessageMapper;
        this.webSocketPushService = webSocketPushService;
        this.objectMapper = objectMapper;
        this.snowflakeIdGenerator = snowflakeIdGenerator;
    }


    @Override
    public String getTopic() {
        return "friend";
    }

    @Override
    public Mono<Void> handleInbound(String userId, String connId, JsonNode payload) {
        String action = payload.path(ACTION).asString("");
        return switch (action) {
            case "request" -> handleRequest(userId, payload);
            case "accept" -> handleAccept(userId, payload);
            case "reject" -> handleReject(userId, payload);
            default -> {
                log.warn("👥 [Friend] 未知操作: action={}, userId={}", action, userId);
                yield Mono.empty();
            }
        };
    }

    /**
     * 发送好友请求 —— 写扩散模型
     * 创建两条记录（发送方 + 接收方），用 relation_id 绑定，一条 SQL 即可双向同步状态
     */
    private Mono<Void> handleRequest(String userId, JsonNode payload) {
        String targetUserId = payload.path("to").asString("");
        if (targetUserId.isEmpty()) {
            log.warn("👥 [Friend] 好友请求缺少目标用户: userId={}", userId);
            return sendResult(userId, FRIEND_REQUEST, false, "缺少目标用户");
        }
        if (targetUserId.equals(userId)) {
            log.warn("👥 [Friend] 不能添加自己为好友: userId={}", userId);
            return sendResult(userId, FRIEND_REQUEST, false, "不能添加自己为好友");
        }
        String msg = payload.path("msg").asString("");
        return timerFriendRequestMapper
                .selectByUserIdAndOpponentId(userId, targetUserId)
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .flatMap(optional -> {
                    if (optional.isEmpty()) {
                        long relationId = snowflakeIdGenerator.nextId();
                        TimerFriendRequest senderRecord = TimerFriendRequest.builder()
                                .userId(userId)
                                .opponentId(targetUserId)
                                .direction(TimerFriendRequest.Direction.SENT.getCode())
                                .status(TimerFriendRequest.Status.PENDING.getCode())
                                .message(msg)
                                .relationId(relationId)
                                .createUser(userId)
                                .updateUser(userId)
                                .build();
                        TimerFriendRequest receiverRecord = TimerFriendRequest.builder()
                                .userId(targetUserId)
                                .opponentId(userId)
                                .direction(TimerFriendRequest.Direction.RECEIVED.getCode())
                                .status(TimerFriendRequest.Status.PENDING.getCode())
                                .message(msg)
                                .relationId(relationId)
                                .createUser(userId)
                                .updateUser(userId)
                                .build();
                        return Mono.zip(
                                        timerFriendRequestMapper.save(senderRecord),
                                        timerFriendRequestMapper.save(receiverRecord)
                                )
                                .flatMap(tuple ->
                                        saveAndPush(targetUserId, FRIEND_REQUEST, userId, tuple.getT2().getId()))
                                .then(sendResult(userId, FRIEND_REQUEST, true, "好友请求已发送"));
                    }
                    TimerFriendRequest existing = optional.get();
                    if (existing.getStatus() == TimerFriendRequest.Status.PENDING.getCode()) {
                        log.info("👥 [Friend] 已存在待处理请求，更新更新时间: {} <-> {}", userId, targetUserId);
                        return timerFriendRequestMapper
                                .updateTimeByRelationId(existing.getRelationId(), LocalDateTime.now())
                                .then(timerFriendRequestMapper
                                        .selectByRelationIdAndUserId(existing.getRelationId(), targetUserId))
                                .flatMap(receiverRecord ->
                                        saveAndPush(targetUserId, FRIEND_REQUEST, userId, receiverRecord.getId()))
                                .then(sendResult(userId, FRIEND_REQUEST, true, "好友请求已发送"));
                    }
                    log.info("👥 [Friend] 请求已处理，无需重复操作: {} <-> {}", userId, targetUserId);
                    return sendResult(userId, FRIEND_REQUEST, false, "该好友请求已处理，无需重复操作");
                })
                .onErrorResume(e -> {
                    log.error("❌ [Friend] 发送好友请求失败: {} -> {}", userId, targetUserId, e);
                    return sendResult(userId, FRIEND_REQUEST, false, "发送失败");
                });
    }

    /**
     * 接受好友请求 —— 通过 relation_id 批量更新双向记录状态
     */
    private Mono<Void> handleAccept(String userId, JsonNode payload) {
        long requestId = payload.path(REQUEST_ID).asLong(0);
        if (requestId == 0) {
            log.warn("👥 [Friend] 接受请求缺少 requestId: userId={}", userId);
            return sendResult(userId, FRIEND_ACCEPT, false, "缺少请求ID");
        }
        return timerFriendRequestMapper.findById(requestId)
                .flatMap(request -> {
                    if (request.getStatus() != TimerFriendRequest.Status.PENDING.getCode()) {
                        log.info("👥 [Friend] 请求已处理，无需重复操作: requestId={}", requestId);
                        return sendResult(userId, FRIEND_ACCEPT, false, "请求已处理，无需重复操作");
                    }
                    if (!userId.equals(request.getUserId())) {
                        log.warn("👥 [Friend] 非目标用户无法接受请求: userId={}, recordUserId={}",
                                userId, request.getUserId());
                        return sendResult(userId, FRIEND_ACCEPT, false, "无权操作此请求");
                    }
                    if (request.getDirection() != TimerFriendRequest.Direction.RECEIVED.getCode()) {
                        log.warn("👥 [Friend] 只能接受收到的请求: userId={}, direction={}", userId, request.getDirection());
                        return sendResult(userId, FRIEND_ACCEPT, false, "只能接受收到的请求");
                    }
                    String opponentId = request.getOpponentId();
                    TimerFriend requestFriend = TimerFriend.builder()
                            .userId(opponentId)
                            .friendId(userId)
                            .createUser(userId)
                            .updateUser(userId)
                            .build();
                    TimerFriend targetFriend = TimerFriend.builder()
                            .userId(userId)
                            .friendId(opponentId)
                            .createUser(userId)
                            .updateUser(userId)
                            .build();
                    return timerFriendRequestMapper
                            .updateStatusByRelationId(request.getRelationId(), userId,
                                    TimerFriendRequest.Status.ACCEPTED.getCode())
                            .then(timerFriendMapper.save(requestFriend))
                            .then(timerFriendMapper.save(targetFriend))
                            .then(saveAndPush(opponentId, FRIEND_ACCEPT, userId, requestId))
                            .then(sendResult(userId, FRIEND_ACCEPT, true, "已接受好友请求"))
                            .doOnSuccess(v -> log.info("👥 [Friend] 好友请求已接受: {} <-> {}",
                                    opponentId, userId));
                })
                .onErrorResume(e -> {
                    log.error("❌ [Friend] 接受好友请求失败: requestId={}", requestId, e);
                    return sendResult(userId, FRIEND_ACCEPT, false, "操作失败");
                });
    }

    /**
     * 拒绝好友请求 —— 通过 relation_id 批量更新双向记录状态
     */
    private Mono<Void> handleReject(String userId, JsonNode payload) {
        long requestId = payload.path(REQUEST_ID).asLong(0);
        if (requestId == 0) {
            log.warn("👥 [Friend] 拒绝请求缺少 requestId: userId={}", userId);
            return sendResult(userId, FRIEND_REJECT, false, "缺少请求ID");
        }
        return timerFriendRequestMapper.findById(requestId)
                .flatMap(request -> {
                    if (request.getStatus() != TimerFriendRequest.Status.PENDING.getCode()) {
                        log.info("👥 [Friend] 请求已处理，无需重复操作: requestId={}", requestId);
                        return sendResult(userId, FRIEND_REJECT, false, "请求已处理，无需重复操作");
                    }
                    if (!userId.equals(request.getUserId())) {
                        log.warn("👥 [Friend] 非目标用户无法拒绝请求: userId={}, recordUserId={}",
                                userId, request.getUserId());
                        return sendResult(userId, FRIEND_REJECT, false, "无权操作此请求");
                    }
                    if (request.getDirection() != TimerFriendRequest.Direction.RECEIVED.getCode()) {
                        log.warn("👥 [Friend] 只能拒绝收到的请求: userId={}, direction={}", userId, request.getDirection());
                        return sendResult(userId, FRIEND_REJECT, false, "只能拒绝收到的请求");
                    }
                    return timerFriendRequestMapper
                            .updateStatusByRelationId(request.getRelationId(), userId,
                                    TimerFriendRequest.Status.REJECTED.getCode())
                            .then(saveAndPush(request.getOpponentId(), FRIEND_REJECT, userId, requestId))
                            .then(sendResult(userId, FRIEND_REJECT, true, "已拒绝好友请求"))
                            .doOnSuccess(v -> log.info("👥 [Friend] 好友请求已拒绝: {} -> {}",
                                    request.getOpponentId(), userId));
                })
                .onErrorResume(e -> {
                    log.error("❌ [Friend] 拒绝好友请求失败: requestId={}", requestId, e);
                    return sendResult(userId, FRIEND_REJECT, false, "操作失败");
                });
    }

    /**
     * 保存离线通知 + 尝试实时推送（best effort）。
     */
    private Mono<Void> saveAndPush(String toUserId, String action,
                                   String fromUserId, long requestId) {
        return saveOfflineMessage(toUserId, action, fromUserId, requestId)
                .then(Mono.defer(() -> pushRealtime(toUserId, action, fromUserId, requestId)));
    }

    /**
     * 持久化离线通知消息到 timer_message 表
     */
    private Mono<Void> saveOfflineMessage(String toUserId, String action,
                                          String fromUserId, long requestId) {
        ObjectNode content = objectMapper.createObjectNode()
                .put(ACTION, action)
                .put(REQUEST_ID, requestId)
                .put("from", fromUserId)
                .put("ts", System.currentTimeMillis());
        TimerMessage msg = new TimerMessage();
        msg.setFromUserId(0L);
        msg.setToUserId(toUserId);
        msg.setMessageType(MSG_TYPE_NOTIFY);
        msg.setContent(content.toString());
        msg.setReadStatus((byte) 0);
        LocalDateTime now = LocalDateTime.now();
        msg.setSentTime(now);
        msg.setCreateTime(now);
        msg.setCreateUser(fromUserId);
        msg.setUpdateUser(fromUserId);
        return timerMessageMapper.save(msg)
                .doOnSuccess(saved -> log.debug("📝 离线通知已保存: to={}, action={}", toUserId, action))
                .onErrorResume(e -> {
                    log.warn("⚠️ 离线通知保存失败: to={}, action={}", toUserId, action, e);
                    return Mono.empty();
                })
                .then();
    }

    /**
     * 实时 WebSocket 推送（best effort，失败仅记录日志）
     */
    private Mono<Void> pushRealtime(String toUserId, String action, String fromUserId, long requestId) {
        ObjectNode data = objectMapper.createObjectNode()
                .put(ACTION, action)
                .put(REQUEST_ID, requestId)
                .put("from", fromUserId)
                .put("ts", System.currentTimeMillis());
        return webSocketPushService.pushToUser(toUserId, NOTIFICATION, data)
                .doOnSuccess(v -> log.info("👥 [Friend] 实时通知已推送: to={}, action={}", toUserId, action))
                .onErrorResume(e -> {
                    log.debug("📭 用户离线或推送失败，通知已入库: to={}, action={}", toUserId, action);
                    return Mono.empty();
                });
    }

    /**
     * 向前端推送操作结果
     */
    private Mono<Void> sendResult(String userId, String operation, boolean success, String message) {
        ObjectNode data = objectMapper.createObjectNode()
                .put(ACTION, operation + "_result")
                .put("success", success)
                .put("message", message)
                .put("ts", System.currentTimeMillis());
        return webSocketPushService.pushToUser(userId, NOTIFICATION, data)
                .doOnSuccess(v -> log.debug("👥 [Friend] 操作结果已推送: to={}, op={}, success={}",
                        userId, operation, success))
                .onErrorResume(e -> {
                    log.warn("⚠️ 推送操作结果失败: to={}, op={}", userId, operation, e);
                    return Mono.empty();
                });
    }
}