package com.rivers.im.router;

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

    public static final String CONTENT = "content";
    private final IWebSocketPushService pushService;
    private final ObjectMapper objectMapper;

    public ChatTopicHandler(IWebSocketPushService pushService, ObjectMapper objectMapper) {
        this.pushService = pushService;
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
        String toUserId = payload.get("to").asString();
        String content = payload.has(CONTENT) ?
                payload.get(CONTENT).asString("") : "";
        log.info("💬 [Chat] {} -> {}: {}", userId, toUserId, content);
        var chatData = objectMapper.createObjectNode()
                .put("from", userId)
                .put("to", toUserId)
                .put(CONTENT, content)
                .put("ts", System.currentTimeMillis());
        return Mono.when(
                pushService.pushToUser(toUserId, "chat", chatData),
                pushService.pushToUser(userId, "chat", chatData)
        ).doOnError(e -> log.error("❌ [Chat] 推送失败: {} -> {}", userId, toUserId, e));
    }
}
