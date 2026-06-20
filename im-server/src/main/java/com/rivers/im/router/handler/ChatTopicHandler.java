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

@Component
@Slf4j
@NullMarked
public class ChatTopicHandler implements TopicHandler {

    private static final String CONTENT = "content";
    private static final String GROUP_PREFIX = "group:";
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
            return Mono.empty();
        }
        String to = payload.get("to").asString();
        String content = payload.has(CONTENT) ?
                payload.get(CONTENT).asString("") : "";
        if (to.startsWith(GROUP_PREFIX)) {
            return handleGroupChat(userId, to, content);
        }
        return handlePrivateChat(userId, to, content);
    }

    private Mono<Void> handlePrivateChat(String userId, String toUserId, String content) {
        log.info("💬 [Chat] {} -> {}: {}", userId, toUserId, content);
        var chatData = objectMapper.createObjectNode()
                .put("from", userId)
                .put("to", toUserId)
                .put(CONTENT, content)
                .put("chatType", "private")
                .put("ts", System.currentTimeMillis());
        return Mono.when(
                webSocketPushService.pushToUser(toUserId, "chat", chatData),
                webSocketPushService.pushToUser(userId, "chat", chatData)
        ).doOnError(e -> log.error("❌ [Chat] 私聊推送失败: {} -> {}", userId, toUserId, e));
    }

    private Mono<Void> handleGroupChat(String userId, String to, String content) {
        long groupId = Long.parseLong(to.substring(GROUP_PREFIX.length()));
        log.info("💬 [Chat] 群聊 {}: {} -> {}", groupId, userId, content);
        var chatData = objectMapper.createObjectNode()
                .put("from", userId)
                .put("to", to)
                .put(CONTENT, content)
                .put("chatType", "group")
                .put("groupId", groupId)
                .put("ts", System.currentTimeMillis());
        return timerGroupMemberMapper.selectByGroupId(groupId)
                .filter(member -> !member.getUserId().equals(userId))
                .flatMap(member ->
                        webSocketPushService.pushToUser(member.getUserId(), "chat", chatData)
                                .onErrorResume(e -> Mono.empty()))
                .then(webSocketPushService.pushToUser(userId, "chat", chatData))
                .then()
                .doOnError(e -> log.error("❌ [Chat] 群聊推送失败: groupId={}, userId={}", groupId, userId, e));
    }
}
