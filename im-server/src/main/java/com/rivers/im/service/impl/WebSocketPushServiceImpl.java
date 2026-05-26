package com.rivers.im.service.impl;

import com.rivers.im.manage.LocalSessionManager;
import com.rivers.im.record.WsEnvelope;
import com.rivers.im.service.IWebSocketPushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebSocketPushServiceImpl implements IWebSocketPushService {

    private final LocalSessionManager sessionManager;

    private final ReactiveStringRedisTemplate redisTemplate;

    private final ObjectMapper objectMapper;

    @Value("${spring.cloud.client.hostname:127.0.0.1}:${server.port:8080}")
    private String currentServerId;


    @Override
    public ObjectNode createObjectNode() {
        return objectMapper.createObjectNode();
    }

    @Override
    public Mono<Void> pushToUser(String userId, String topic, Object payload) {
        return Mono.fromCallable(() -> {
                    WsEnvelope envelope = new WsEnvelope(topic, UUID.randomUUID().toString(),
                            objectMapper.valueToTree(payload));
                    return objectMapper.writeValueAsString(envelope);
                })
                .flatMap(jsonMsg -> routeToUser(userId, jsonMsg));
    }

    private Mono<Void> routeToUser(String userId, String jsonMsg) {
        String routeKey = "ws:route:" + userId;
        return redisTemplate.opsForHash().entries(routeKey)
                .collectMap(e -> e.getKey().toString(), e -> e.getValue().toString())
                .flatMap(connections -> {
                    if (connections.isEmpty()) {
                        log.debug("📭 用户 {} 离线", userId);
                        return Mono.empty();
                    }
                    return Mono.when(connections.entrySet().stream().map(entry -> {
                        String connId = entry.getKey();
                        String targetServerId = entry.getValue();

                        if (targetServerId.equals(currentServerId)) {
                            // 1. 本机连接：直接通过 Manager 推入 Sinks 队列
                            sessionManager.pushToLocal(connId, jsonMsg);
                            return Mono.empty();
                        } else {
                            // 2. 跨服连接：发 Redis Pub/Sub
                            ObjectNode crossMsg = objectMapper.createObjectNode()
                                    .put("connId", connId)
                                    .put("payload", jsonMsg);
                            return redisTemplate.convertAndSend("ws:node:" + targetServerId,
                                            crossMsg.toString())
                                    .then();
                        }
                    }).toList());
                });
    }
}
