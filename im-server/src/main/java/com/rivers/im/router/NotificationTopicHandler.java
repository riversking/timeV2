package com.rivers.im.router;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;

@Component
@Slf4j
@NullMarked
public class NotificationTopicHandler implements TopicHandler {
    @Override
    public String getTopic() {
        return "notification";
    }

    @Override
    public Mono<Void> handleInbound(String userId, String connId, JsonNode payload) {
        return Mono.fromRunnable(() -> {
            String action = payload.path("action").asString("");
            if ("read".equals(action)) {
                log.info("🔔 [Notify] User {} read msg {}", userId, payload.path("id").asString());
            }
        });
    }
}