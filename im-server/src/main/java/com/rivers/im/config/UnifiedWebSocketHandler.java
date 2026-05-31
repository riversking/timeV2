package com.rivers.im.config;

import com.rivers.im.context.ConnectionContext;
import com.rivers.im.manage.LocalSessionManager;
import com.rivers.im.record.WsEnvelope;
import com.rivers.im.router.TopicHandler;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.ReactiveSubscription;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@Slf4j
@NullMarked
public class UnifiedWebSocketHandler implements WebSocketHandler {

    private final Map<String, TopicHandler> routerMap;
    private final ObjectMapper objectMapper;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final ReactiveRedisMessageListenerContainer listenerContainer;
    private final LocalSessionManager sessionManager;
    private final String currentServerId;

    @Nullable
    private Disposable crossServerSubscription;

    public UnifiedWebSocketHandler(List<TopicHandler> handlers,
                                   ObjectMapper objectMapper,
                                   ReactiveStringRedisTemplate redisTemplate,
                                   ReactiveRedisMessageListenerContainer listenerContainer,
                                   LocalSessionManager sessionManager,
                                   @Value("${spring.cloud.client.hostname:127.0.0.1}:${server.port:8080}")
                                   String currentServerId) {
        this.routerMap = handlers.stream()
                .collect(Collectors.toMap(TopicHandler::getTopic, Function.identity()));
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
        this.listenerContainer = listenerContainer;
        this.sessionManager = sessionManager;
        this.currentServerId = currentServerId;
        log.info("🚀 网关注册 Topics: {}", routerMap.keySet());
    }

    @PostConstruct
    public void init() {
        String channel = "ws:node:" + currentServerId;
        crossServerSubscription = listenerContainer.receive(ChannelTopic.of(channel))
                .map(ReactiveSubscription.Message::getMessage)
                .subscribe(
                        this::handleCrossServerMessage,
                        e -> log.error("❌ Redis Pub/Sub 监听异常，跨服消息将不可用", e)
                );
        log.info("📡 节点 [{}] 已订阅跨服频道", currentServerId);
    }

    @PreDestroy
    public void destroy() {
        if (crossServerSubscription != null && !crossServerSubscription.isDisposed()) {
            crossServerSubscription.dispose();
            log.info("🧹 已取消跨服频道订阅: {}", currentServerId);
        }
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        String connId = UUID.randomUUID().toString();
        String userId = extractUserId(session);
        if (StringUtils.isBlank(userId)) {
            log.warn("⚠️ 无法获取 userId，关闭连接: connId={}", connId);
            return session.close();
        }
        ConnectionContext ctx = new ConnectionContext(session, userId);
        sessionManager.register(connId, ctx);
        String routeKey = "ws:route:" + userId;
        Mono<Void> register = redisTemplate.opsForHash()
                .put(routeKey, connId, currentServerId)
                .then(redisTemplate.expire(routeKey, Duration.ofMinutes(5)))
                .doOnError(e -> log.error("❌ Redis 路由注册失败: userId={}, connId={}", userId, connId, e))
                .then();
        Mono<Void> output = session.send(
                ctx.getOutboundSink().asFlux().map(session::textMessage)
        );
        Mono<Void> input = session.receive()
                .map(WebSocketMessage::getPayloadAsText)
                .concatMap(raw -> dispatchMessage(userId, connId, raw), 1)
                .then();

        Flux<Void> heartbeat = Flux.interval(Duration.ofSeconds(25))
                .takeWhile(tick -> session.isOpen())
                .concatMap(tick ->
                        redisTemplate.expire(routeKey, Duration.ofMinutes(5))
                                .doOnError(e -> log.warn("⚠️ 心跳续期失败: userId={}, connId={}", userId, connId))
                                .onErrorComplete()
                                .then()
                );
        return Mono.when(register, input, output, heartbeat.then())
                .doOnError(e -> log.error("❌ WebSocket 流异常: userId={}, connId={}", userId, connId, e))
                .doFinally(sig -> cleanup(connId, userId));
    }

    private Mono<Void> dispatchMessage(String userId, String connId, String raw) {
        return Mono.fromCallable(() -> objectMapper.readValue(raw, WsEnvelope.class))
                .flatMap(env -> {
                    TopicHandler handler = routerMap.get(env.topic());
                    if (handler == null) {
                        log.warn("⚠️ 未知 Topic: {}", env.topic());
                        return Mono.empty();
                    }
                    return handler.handleInbound(userId, connId, env.payload());
                })
                .onErrorResume(e -> {
                    log.warn("⚠️ 消息解析或路由失败: {}", e.getMessage());
                    return Mono.empty();
                });
    }

    private void handleCrossServerMessage(String json) {
        try {
            var node = objectMapper.readTree(json);
            String connId = node.get("connId").asString();
            String payload = node.get("payload").asString();
            sessionManager.pushToLocal(connId, payload);
        } catch (Exception e) {
            log.error("❌ 跨服消息处理失败", e);
        }
    }

    private void cleanup(String connId, String userId) {
        sessionManager.unregister(connId);
        String routeKey = "ws:route:" + userId;
        redisTemplate.opsForHash()
                .remove(routeKey, connId)
                .subscribe(
                        result -> {
                        },
                        e -> log.warn("⚠️ Redis 路由清理失败: connId={}, userId={}", connId, userId, e)
                );
        log.info("🧹 连接清理: {} | user: {}", connId, userId);
    }

    private @Nullable String extractUserId(WebSocketSession session) {
        Object obj = session.getAttributes().get("userId");
        if (obj != null) {
            return obj.toString();
        }
        URI uri = session.getHandshakeInfo().getUri();
        if (uri.getQuery() != null) {
            for (String param : uri.getQuery().split("&")) {
                String[] kv = param.split("=", 2);
                if (kv.length == 2 && "userId".equals(kv[0])) {
                    return kv[1];
                }
            }
        }
        return null;
    }
}
