package com.rivers.im.service.impl;

import com.rivers.im.manage.LocalSessionManager;
import com.rivers.im.record.WsEnvelope;
import com.rivers.im.service.IWebSocketPushService;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.UUID;

@Service
@Slf4j
@NullMarked
public class WebSocketPushServiceImpl implements IWebSocketPushService {

    private final LocalSessionManager sessionManager;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final String currentServerId;

    public WebSocketPushServiceImpl(
            LocalSessionManager sessionManager,
            ReactiveStringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${spring.cloud.client.hostname:127.0.0.1}:${server.port:8080}")
            String currentServerId) {
        this.sessionManager = sessionManager;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.currentServerId = currentServerId;
    }

    @Override
    public ObjectNode createObjectNode() {
        return objectMapper.createObjectNode();
    }

    @Override
    public Mono<Void> pushToUser(String userId, String topic, Object payload) {
        return Mono.fromCallable(() -> {
                    WsEnvelope envelope = new WsEnvelope(
                            topic,
                            UUID.randomUUID().toString(),
                            objectMapper.valueToTree(payload));
                    return objectMapper.writeValueAsString(envelope);
                })
                .flatMap(jsonMsg -> routeToUser(userId, jsonMsg));
    }

    private Mono<Void> routeToUser(String userId, String jsonMsg) {
        String routeKey = "ws:route:" + userId;
        return redisTemplate.opsForHash().entries(routeKey)
                .collectMap(
                        e -> e.getKey().toString(),
                        e -> e.getValue().toString())
                .flatMap(connections -> {
                    if (connections.isEmpty()) {
                        log.debug("📭 用户 {} 离线", userId);
                        return Mono.empty();
                    }
                    return Mono.when(connections.entrySet().stream()
                            .map(entry -> pushToConnection(
                                    entry.getKey(),
                                    entry.getValue(),
                                    jsonMsg))
                            .toList());
                });
    }

    private Mono<Void> pushToConnection(String connId, String targetServerId, String jsonMsg) {
        if (targetServerId.equals(currentServerId)) {
            sessionManager.pushToLocal(connId, jsonMsg);
            return Mono.empty();
        }
        ObjectNode crossMsg = objectMapper.createObjectNode()
                .put("connId", connId)
                .put("payload", jsonMsg);
        return redisTemplate.convertAndSend("ws:node:" + targetServerId, crossMsg.toString())
                .doOnError(e -> log.warn("⚠️ 跨服推送失败: target={}, connId={}", targetServerId, connId, e))
                .onErrorResume(e -> Mono.empty())
                .then();
    }
}
