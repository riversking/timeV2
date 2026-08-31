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
import reactor.util.retry.Retry;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

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
        // 订阅是"永久频道"：无限退避重试（1s → 上限 30s），绝不静默死亡
        crossServerSubscription = listenerContainer.receive(ChannelTopic.of(channel))
                .map(ReactiveSubscription.Message::getMessage)
                .retryWhen(Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(1))
                        .maxBackoff(Duration.ofSeconds(30))
                        .doBeforeRetry(rs -> log.warn("🔁 Redis Pub/Sub 订阅重试: attempt={}, error={}",
                                rs.totalRetries() + 1,
                                rs.failure() != null ? rs.failure().getMessage() : "unknown")))
                .subscribe(
                        this::handleCrossServerMessage,
                        e -> log.error("❌ Redis Pub/Sub 监听异常，跨服消息将不可用", e)
                );
        log.info("📡 节点 [ {}] 已订阅跨服频道", currentServerId);
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
                                .doOnError(_ -> log.warn("⚠️ 心跳续期失败: userId={}, connId={}",
                                        userId, connId))
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
                    if (StringUtils.isBlank(env.topic()) || env.payload() == null) {
                        return Mono.error(new IllegalArgumentException("消息格式不合法: topic 或 payload 缺失"));
                    }
                    TopicHandler handler = routerMap.get(env.topic());
                    if (handler == null) {
                        return Mono.error(new IllegalArgumentException("未知 Topic: " + env.topic()));
                    }
                    return handler.handleInbound(userId, connId, env.payload());
                })
                .onErrorResume(e -> {
                    log.warn("⚠️ 消息解析或路由失败: connId={}, error={}", connId, e.getMessage());
                    return sendErrorResult(connId, e.getMessage());
                });
    }

    /**
     * 错误反馈闭环：解析/路由失败以 system topic 推回客户端，
     * 客户端不再"消息石沉大海"。
     */
    private Mono<Void> sendErrorResult(String connId, String message) {
        return Mono.fromCallable(() -> {
                    ObjectNode errorPayload = objectMapper.createObjectNode()
                            .put("code", 400)
                            .put("message", message == null ? "消息处理失败" : message);
                    WsEnvelope error = new WsEnvelope("system", UUID.randomUUID().toString(), errorPayload);
                    return objectMapper.writeValueAsString(error);
                })
                .doOnNext(json -> sessionManager.pushToLocal(connId, json))
                .onErrorResume(e -> {
                    log.warn("⚠️ 错误反馈序列化失败: connId={}", connId, e);
                    return Mono.empty();
                })
                .then();
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
        // 仅信任握手装饰器注入的 userId；删除 query 参数兜底，堵死未鉴权直连旁路
        Object obj = session.getAttributes().get("userId");
        return obj != null ? obj.toString() : null;
    }
}