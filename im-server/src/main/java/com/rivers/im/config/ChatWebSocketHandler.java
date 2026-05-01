package com.rivers.im.config;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.ReactiveSubscription;
import org.springframework.data.redis.core.ReactiveHashOperations;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import tools.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static java.time.Duration.ofMinutes;
import static java.time.Duration.ofSeconds;

@Component
@Slf4j
public class ChatWebSocketHandler implements WebSocketHandler {

    private final ReactiveRedisConnectionFactory connectionFactory;

    private final ReactiveRedisTemplate<String, String> reactiveRedisTemplate;

    private final ObjectMapper objectMapper; // Spring Boot 自动配置的 ObjectMapper


    @Getter
    @Value("${spring.cloud.client.hostname}:${server.port}")
    private String serverId;

    public ChatWebSocketHandler(ReactiveRedisConnectionFactory connectionFactory,
                                ReactiveRedisTemplate<String, String> reactiveRedisTemplate, ObjectMapper objectMapper) {
        this.connectionFactory = connectionFactory;
        this.reactiveRedisTemplate = reactiveRedisTemplate;
        this.objectMapper = objectMapper;
    }


    // 本地仅存储【当前实例】的活跃连接
    private final Map<String, ConnectionInfo> localConnections = new ConcurrentHashMap<>();

    // Redis 订阅资源管理
    private ReactiveRedisMessageListenerContainer listenerContainer;
    private Disposable instanceSubscription;
    private Disposable broadcastSubscription;

    // Redis 操作优化（避免重复创建）
    private ReactiveHashOperations<String, String, String> hashOps;
    private ReactiveValueOperations<String, String> valueOps;

    @PostConstruct
    public void init() {
        // 创建容器（无需 .build()）
        listenerContainer = new ReactiveRedisMessageListenerContainer(connectionFactory);

        // 订阅实例频道
        String instanceChannel = "ws:msg:" + serverId;
        instanceSubscription = listenerContainer.receive(ChannelTopic.of(instanceChannel))
                .map(ReactiveSubscription.Message::getMessage)  // Message<String, String> → String
                .subscribe(
                        this::routeMessageToLocalSession,
                        error -> log.error("❌ Instance channel [{}] error", instanceChannel, error),
                        () -> log.info("✅ Instance channel [{}] subscription completed", instanceChannel)
                );

        // 订阅广播频道
        broadcastSubscription = listenerContainer.receive(ChannelTopic.of("ws:broadcast"))
                .map(ReactiveSubscription.Message::getMessage)  // ✅ Message<String, String> → String
                .subscribe(
                        this::broadcastToLocalSessions,
                        error -> log.error("❌ Broadcast channel error", error),
                        () -> log.info("✅ Broadcast channel subscription completed")
                );

        log.info("🚀 WebSocketHandler initialized | serverId: {} | Channels: [{}], [ws:broadcast]",
                serverId, instanceChannel);
    }

    @PreDestroy
    public void destroy() {
        // 安全清理订阅资源
        if (instanceSubscription != null && !instanceSubscription.isDisposed()) {
            instanceSubscription.dispose();
        }
        if (broadcastSubscription != null && !broadcastSubscription.isDisposed()) {
            broadcastSubscription.dispose();
        }

        if (listenerContainer != null) {
            listenerContainer.destroy();
        }

        // 清理所有本地连接（触发 cleanup）
        localConnections.keySet().forEach(connId -> {
            ConnectionInfo info = localConnections.get(connId);
            if (info != null) {
                cleanupConnection(connId, info.getUserId());
            }
        });

        log.info("🧹 ChatWebSocketHandler destroyed | Cleared {} local connections", localConnections.size());
    }

    @Override
    @NullMarked
    public Mono<Void> handle(WebSocketSession session) {
        String connectionId = UUID.randomUUID().toString();
        Long userId = extractUserId(session);

        if (userId == null) {
            log.warn("❌ Rejected connection: userId not found in session attributes");
            return session.close();
        }

        // 1. 本地注册连接
        localConnections.put(connectionId, new ConnectionInfo(session, userId));
        log.info("✅ User {} connected | connId: {} | serverId: {}", userId, connectionId, serverId);

        // 2. Redis 注册路由（用户→连接映射 + 连接元数据）
        String userKey = "ws:user:" + userId;
        String connKey = "ws:conn:" + connectionId;

        Map<String, String> connMeta = Map.of(
                "serverId", serverId,
                "userId", userId.toString(),
                "connectTime", String.valueOf(System.currentTimeMillis())
        );

        Mono<Void> register = hashOps.put(userKey, connectionId, serverId)
                .then(hashOps.putAll(connKey, connMeta))
                .then(reactiveRedisTemplate.expire(userKey, ofMinutes(5)))
                .then(reactiveRedisTemplate.expire(connKey, ofMinutes(5)))
                .then(updateOnlineStatus(userId, true))
                .doOnSuccess(v -> log.debug("✅ Registered connection in Redis | userKey: {}, connKey: {}", userKey, connKey))
                .onErrorResume(e -> {
                    log.error("❌ Failed to register connection in Redis", e);
                    cleanupConnection(connectionId, userId);
                    return Mono.empty();
                });

        // 3. 消息接收处理
        Mono<Void> input = session.receive()
                .map(WebSocketMessage::getPayloadAsText)
                .doOnNext(msg -> handleClientMessage(connectionId, userId, msg))
                .doOnError(e -> log.error("❌ Error receiving message from user {} (connId: {})", userId, connectionId, e))
                .doOnTerminate(() -> log.debug("📡 Input stream terminated for connId: {}", connectionId))
                .then();

        // 4. 心跳续期（每25秒，持续到 session 关闭）
        Flux<Void> heartbeatFlux = Flux.interval(Duration.ofSeconds(25))
                .takeWhile(tick -> session.isOpen()) // ✅ Boolean 过滤，但不影响返回类型
                .flatMap(tick ->
                        // 🔑 核心修正：将 Mono<Boolean> 转换为 Mono<Void>
                        Mono.when(
                                        reactiveRedisTemplate.expire(userKey, Duration.ofMinutes(5)).then(), // ✅ .then() 转 Void
                                        reactiveRedisTemplate.expire(connKey, Duration.ofMinutes(5)).then()
                                )
                                .doOnError(e -> log.warn("⚠️ Heartbeat renew failed for connId: {}", connectionId, e))
                                .onErrorResume(e -> Mono.empty())
                )
                .onErrorContinue((e, _) -> log.warn("⚠️ Heartbeat flux error for connId: {}", connectionId, e));

        // 5. 合并流 + 清理资源
        return Mono.when(register, input, heartbeatFlux.then())
                .doFinally(signalType -> cleanupConnection(connectionId, userId))
                .then();
    }

    // ==================== 消息路由核心 ====================

    /**
     * 发送消息给指定用户（支持多端登录）
     */
    public void sendMessageToUser(Long userId, String payload) {
        String userKey = "ws:user:" + userId;

        hashOps.entries(userKey)
                .collectMap(Map.Entry::getKey, Map.Entry::getValue) // connectionId → serverId
                .defaultIfEmpty(Collections.emptyMap())
                .subscribe(connections -> {
                    if (connections.isEmpty()) {
                        log.debug("📭 User {} is offline, message stored (handled by caller)", userId);
                        return;
                    }

                    connections.forEach((connId, targetServer) -> {
                        if (targetServer.equals(serverId)) {
                            // 本实例连接：直接推送
                            ConnectionInfo info = localConnections.get(connId);
                            if (info != null && info.getSession().isOpen()) {
                                info.getSession().send(Mono.just(info.getSession().textMessage(payload)))
                                        .onErrorResume(e -> {
                                            log.warn("⚠️ Failed to send to local connId: {} (user: {})", connId, userId, e);
                                            return Mono.empty();
                                        })
                                        .subscribe();
                            } else {
                                log.debug("⚠️ Local session not found or closed: connId={}, user={}", connId, userId);
                            }
                        } else {
                            // 跨实例：通过 Redis Pub/Sub 路由
                            try {
                                Map<String, String> routedMsg = Map.of(
                                        "connectionId", connId,
                                        "payload", payload
                                );
                                String channel = "ws:msg:" + targetServer;
                                String jsonMsg = objectMapper.writeValueAsString(routedMsg);

                                reactiveRedisTemplate.convertAndSend(channel, jsonMsg)
                                        .doOnSuccess(v -> log.trace("📤 Routed message to server: {} | connId: {}", targetServer, connId))
                                        .doOnError(e -> log.error("❌ Failed to route message to server: {}", targetServer, e))
                                        .subscribe();
                            } catch (Exception e) {
                                log.error("❌ Error building routed message for connId: {}", connId, e);
                            }
                        }
                    });
                }, error -> log.error("❌ Error querying connections for user: {}", userId, error));
    }

    /**
     * 广播消息到所有在线用户（跨实例）
     */
    public void broadcastMessage(String payload) {
        reactiveRedisTemplate.convertAndSend("ws:broadcast", payload)
                .doOnSuccess(v -> log.debug("📢 Broadcast message sent"))
                .doOnError(e -> log.error("❌ Broadcast failed", e))
                .subscribe();
    }

    // ==================== 消息处理回调 ====================

    private void routeMessageToLocalSession(String routedJson) {
        try {
            Map<?, ?> msg = objectMapper.readValue(routedJson, Map.class);
            String connId = (String) msg.get("connectionId");
            String payload = (String) msg.get("payload");

            ConnectionInfo info = localConnections.get(connId);
            if (info != null && info.getSession().isOpen()) {
                info.getSession().send(Mono.just(info.getSession().textMessage(payload)))
                        .onErrorResume(e -> {
                            log.warn("⚠️ Failed to deliver routed message to connId: {}", connId, e);
                            return Mono.empty();
                        })
                        .subscribe();
                log.trace("✅ Delivered routed message to local connId: {}", connId);
            } else {
                log.debug("⚠️ Target session not found or closed: connId={}", connId);
            }
        } catch (Exception e) {
            log.error("❌ Error routing message to local session", e);
        }
    }

    private void broadcastToLocalSessions(String payload) {
        int delivered = 0;
        for (ConnectionInfo info : localConnections.values()) {
            if (info.getSession().isOpen()) {
                info.getSession().send(Mono.just(info.getSession().textMessage(payload)))
                        .onErrorResume(e -> {
                            log.warn("⚠️ Broadcast delivery failed to user: {}", info.getUserId(), e);
                            return Mono.empty();
                        })
                        .subscribe();
                delivered++;
            }
        }
        log.debug("✅ Broadcast delivered to {} local sessions", delivered);
    }

    // ==================== 辅助方法 ====================

    private Long extractUserId(WebSocketSession session) {
        Object userIdObj = session.getAttributes().get("userId");
        if (userIdObj instanceof Number userId) {
            return userId.longValue();
        } else if (userIdObj instanceof String userId) {
            try {
                return Long.parseLong(userId);
            } catch (NumberFormatException e) {
                log.warn("⚠️ Invalid userId format in session attributes: {}", userIdObj);
            }
        }
        return null;
    }

    private void handleClientMessage(String connectionId, Long userId, String rawMessage) {
        try {
            // 此处应调用业务逻辑处理消息（示例简化）
            log.debug("📨 Received from user {} (connId={}): {}", userId, connectionId, rawMessage);
            // TODO: 调用 messageService 处理业务逻辑
        } catch (Exception e) {
            log.error("❌ Error processing client message", e);
        }
    }

    private Mono<Void> updateOnlineStatus(Long userId, boolean online) {
        String key = "user:online:" + userId;
        return online
                ? valueOps.set(key, "1", ofMinutes(5)).then()
                : reactiveRedisTemplate.delete(key).then();
    }

    private void cleanupConnection(String connectionId, Long userId) {
        // 1. 移除本地连接
        localConnections.remove(connectionId);
        // 2. 清理 Redis 数据（异步执行，避免阻塞）
        String userKey = "ws:user:" + userId;
        String connKey = "ws:conn:" + connectionId;

        hashOps.remove(userKey, connectionId)
                .then(reactiveRedisTemplate.delete(connKey))
                .then(updateOnlineStatus(userId, false))
                .doOnSuccess(v -> log.info("🧹 Cleaned connection | connId: {} | user: {}", connectionId, userId))
                .doOnError(e -> log.error("❌ Error cleaning Redis for connId: {}", connectionId, e))
                .subscribeOn(Schedulers.boundedElastic()) // 避免阻塞 Netty 线程
                .subscribe();
    }

    // ==================== 内部类 ====================

    @Data
    @AllArgsConstructor
    private static class ConnectionInfo {
        private final WebSocketSession session;
        private final Long userId;
    }

    // ==================== 诊断方法（可选） ====================

    public int getLocalConnectionCount() {
        return localConnections.size();
    }

}
