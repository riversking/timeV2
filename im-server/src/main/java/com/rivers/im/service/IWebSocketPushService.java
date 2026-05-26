package com.rivers.im.service;

import reactor.core.publisher.Mono;
import tools.jackson.databind.node.ObjectNode;

public interface IWebSocketPushService {

    ObjectNode createObjectNode();

    Mono<Void> pushToUser(String userId, String topic, Object payload);
}
