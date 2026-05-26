package com.rivers.im.router;

import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;

public interface TopicHandler {

    String getTopic();

    Mono<Void> handleInbound(String userId, String connId, JsonNode payload);
}
