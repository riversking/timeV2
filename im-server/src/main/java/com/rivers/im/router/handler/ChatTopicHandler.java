package com.rivers.im.router.handler;

import com.rivers.im.mapper.TimerGroupMemberMapper;
import com.rivers.im.router.TopicHandler;
import com.rivers.im.service.IWebSocketPushService;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Component
@Slf4j
@NullMarked
public class ChatTopicHandler implements TopicHandler {

    private static final String CONTENT = "content";
    private static final String ACTION = "action";
    private static final String NOTIFICATION = "notification";
    private static final String GROUP_PREFIX = "group:";
    private static final String MSG_ID = "msgId";

    private final IWebSocketPushService webSocketPushService;
    private final TimerGroupMemberMapper timerGroupMemberMapper;
    private final ObjectMapper objectMapper;

    public ChatTopicHandler(IWebSocketPushService pushService, TimerGroupMemberMapper timerGroupMemberMapper,
                            ObjectMapper objectMapper) {
        this.webSocketPushService = pushService;
        this.timerGroupMemberMapper = timerGroupMemberMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getTopic() {
        return "chat";
    }

    @Override
    public Mono<Void> handleInbound(String userId, String connId, JsonNode payload) {
        if (!payload.has("to") || payload.get("to").isNull()) {
            log.warn("💬 [Chat] 消息缺少接收方: userId={}", userId);
            return sendResult(userId, payload, false, "缺少接收方");
        }
        String to = payload.get("to").asString();
        String content = payload.has(CONTENT) ? payload.get(CONTENT).asString("") : "";
        if (content.isEmpty()) {
            return sendResult(userId, payload, false, "消息内容不能为空");
        }
        if (to.startsWith(GROUP_PREFIX)) {
            return handleGroupChat(userId, to, content, payload);
        }
        return handlePrivateChat(userId, to, content, payload);
    }

    // ======================== 私聊 ========================
    private Mono<Void> handlePrivateChat(String userId, String toUserId, String content, JsonNode payload) {
        log.info("💬 [Chat] {} -> {}: {}", userId, toUserId, content);
        var chatData = objectMapper.createObjectNode()
                .put("from", userId)
                .put("to", toUserId)
                .put(CONTENT, content)
                .put("chatType", "private")
                .put("ts", System.currentTimeMillis());
        // ✅ 只推送给接收方，不再回显给发送者
        //    发送者的确认由下方的 sendResult 通知负责
        return webSocketPushService.pushToUser(toUserId, "chat", chatData)
                .then(sendResult(userId, payload, true, "发送成功"))
                .onErrorResume(e -> {
                    log.error("❌ [Chat] 私聊推送失败: {} -> {}", userId, toUserId, e);
                    return sendResult(userId, payload, false, "发送失败");
                });
    }

    // ======================== 群聊 ========================
    private Mono<Void> handleGroupChat(String userId, String to, String content, JsonNode payload) {
        long groupId = Long.parseLong(to.substring(GROUP_PREFIX.length()));
        log.info("💬 [Chat] 群聊 {}: {} -> {}", groupId, userId, content);
        var chatData = objectMapper.createObjectNode()
                .put("from", userId)
                .put("to", to)
                .put(CONTENT, content)
                .put("chatType", "group")
                .put("groupId", groupId)
                .put("ts", System.currentTimeMillis());
        // ✅ 推送给群内所有非发送者成员，发送者只收 sendResult
        return timerGroupMemberMapper.selectByGroupId(groupId)
                .filter(member -> !member.getUserId().equals(userId))
                .flatMap(member ->
                        webSocketPushService.pushToUser(member.getUserId(), "chat", chatData)
                                .onErrorResume(e -> Mono.empty()))
                .then(sendResult(userId, payload, true, "发送成功"))
                .onErrorResume(e -> {
                    log.error("❌ [Chat] 群聊推送失败: groupId={}, userId={}", groupId, userId, e);
                    return sendResult(userId, payload, false, "发送失败");
                });
    }

    // ======================== 结果反馈 ========================
    private Mono<Void> sendResult(String userId, JsonNode payload, boolean success, String message) {
        String msgId = payload.has(MSG_ID) ? payload.get(MSG_ID).asString() : "";
        ObjectNode data = objectMapper.createObjectNode()
                .put(ACTION, "chat_send" + "_result")
                .put("success", success)
                .put("message", message)
                .put(MSG_ID, msgId)
                .put("ts", System.currentTimeMillis());
        return webSocketPushService.pushToUser(userId, NOTIFICATION, data)
                .doOnSuccess(v -> log.debug("💬 [Chat] 操作结果已推送: to={}, op={}, success={}",
                        userId, "chat_send", success))
                .onErrorResume(e -> {
                    log.warn("⚠️ 推送操作结果失败: to={}, op={}", userId, "chat_send", e);
                    return Mono.empty();
                });
    }
}