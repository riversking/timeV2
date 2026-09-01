package com.rivers.im.service.impl;

import com.rivers.im.constant.CrossServerChannel;
import com.rivers.im.manage.LocalSessionManager;
import com.rivers.im.record.WsEnvelope;
import com.rivers.im.service.IWebSocketPushService;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.Duration;
import java.util.Map;
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
        String streamKey = CrossServerChannel.streamKeyOf(targetServerId);
        Map<String, String> fields = Map.of("connId", connId, "payload", jsonMsg);
        // XADD（目标节点宕机期间消息仍持久在流中，恢复后消费组自动补投）+ MAXLEN 裁剪
        return redisTemplate.opsForStream()
                .add(streamKey, fields)
                .retryWhen(Retry.backoff(2, Duration.ofMillis(300)))
                .then(redisTemplate.opsForStream()
                        .trim(streamKey, CrossServerChannel.MAX_LEN))
                .doOnError(e -> log.warn("⚠️ 跨服推送失败(重试后): target={}, connId={}",
                        targetServerId, connId, e))
                .onErrorResume(e -> Mono.empty())
                .then();
    }
}