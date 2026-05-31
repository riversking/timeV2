package com.rivers.im.router;

import org.jspecify.annotations.NullMarked;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;

@NullMarked
public interface TopicHandler {

    String getTopic();

    Mono<Void> handleInbound(String userId, String connId, JsonNode payload);
}
