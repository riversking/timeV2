package com.rivers.im.router;

import com.rivers.im.service.IWebSocketPushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
@Slf4j
public class ChatTopicHandler implements TopicHandler {

    private final IWebSocketPushService pushService;

    private final ObjectMapper objectMapper;

    @Override
    public String getTopic() {
        return "chat";
    }

    @Override
    public Mono<Void> handleInbound(String userId, String connId, JsonNode payload) {
        return Mono.fromRunnable(() -> {
            String toUserId = payload.get("to").asString();
            String content = payload.get("content").asString();
            log.info("💬 [Chat] {} -> {}: {}", userId, toUserId, content);
            // 构建回显与推送的 Payload
            var chatData = objectMapper.createObjectNode()
                    .put("from", userId)
                    .put("to", toUserId)
                    .put("content", content)
                    .put("ts", System.currentTimeMillis());
            // 推送给接收方 & 回显给发送方 (多端同步)
            pushService.pushToUser(toUserId, "chat", chatData).subscribe();
            pushService.pushToUser(userId, "chat", chatData).subscribe();
        });
    }
}