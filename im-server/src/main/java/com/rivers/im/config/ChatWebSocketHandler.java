package com.rivers.im.config;

import com.alibaba.nacos.shaded.com.google.gson.JsonSyntaxException;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.rivers.im.service.IMessageService;
import com.rivers.im.vo.ChatMessage;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
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
import org.springframework.web.socket.TextMessage;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static java.time.Duration.ofMinutes;

@Component
@Slf4j
public class ChatWebSocketHandler implements WebSocketHandler {

    public static final String SYSTEM = "system";
    public static final String WS_USER = "ws:user:";

    private final IMessageService messageService;

    private final ReactiveRedisConnectionFactory connectionFactory;

    private final ReactiveRedisTemplate<String, String> reactiveRedisTemplate;

    private final ObjectMapper objectMapper; // Spring Boot 自动配置的 ObjectMapper

    @Getter
    @Value("${spring.cloud.client.hostname}:${server.port}")
    private String serverId;

    public ChatWebSocketHandler(IMessageService messageService, ReactiveRedisConnectionFactory connectionFactory,
                                ReactiveRedisTemplate<String, String> reactiveRedisTemplate, ObjectMapper objectMapper) {
        this.messageService = messageService;
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


    private final Gson gson = new GsonBuilder()
            .setDateFormat("yyyy-MM-dd HH:mm:ss")
            .serializeNulls() // 序列化null值
            .create();

    @PostConstruct
    public void init() {
        hashOps = reactiveRedisTemplate.opsForHash();
        valueOps = reactiveRedisTemplate.opsForValue();
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
                cleanupConnection(connId, info.userId());
            }
        });
        log.info("🧹 ChatWebSocketHandler destroyed | Cleared {} local connections", localConnections.size());
    }

    @Override
    @NullMarked
    public Mono<Void> handle(WebSocketSession session) {
        String connectionId = UUID.randomUUID().toString();
        String userId = extractUserId(session);
        if (StringUtils.isBlank(userId)) {
            log.warn("❌ Rejected connection: userId not found in session attributes");
            return session.close();
        }
        // 1. 本地注册连接
        localConnections.put(connectionId, new ConnectionInfo(session, userId));
        log.info("✅ User {} connected | connId: {} | serverId: {}", userId, connectionId, serverId);

        // 2. Redis 注册路由（用户→连接映射 + 连接元数据）
        String userKey = WS_USER + userId;
        String connKey = "ws:conn:" + connectionId;

        Map<String, String> connMeta = Map.of(
                "serverId", serverId,
                "userId", userId,
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
                .takeWhile(_ -> session.isOpen()) // ✅ Boolean 过滤，但不影响返回类型
                .flatMap(_ ->
                        // 🔑 核心修正：将 Mono<Boolean> 转换为 Mono<Void>
                        // ✅ .then() 转 Void
                        Mono.when(
                                        reactiveRedisTemplate.expire(userKey, Duration.ofMinutes(5)).then(),
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
    public void sendMessageToUser(String userId, String payload) {
        String userKey = WS_USER + userId;

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
                            if (info != null && info.session().isOpen()) {
                                info.session().send(Mono.just(info.session().textMessage(payload)))
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
            if (info != null && info.session().isOpen()) {
                info.session().send(Mono.just(info.session().textMessage(payload)))
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
            if (info.session().isOpen()) {
                info.session().send(Mono.just(info.session().textMessage(payload)))
                        .onErrorResume(e -> {
                            log.warn("⚠️ Broadcast delivery failed to user: {}", info.userId(), e);
                            return Mono.empty();
                        })
                        .subscribe();
                delivered++;
            }
        }
        log.debug("✅ Broadcast delivered to {} local sessions", delivered);
    }

    // ==================== 辅助方法 ====================

    private String extractUserId(WebSocketSession session) {
        Object userIdObj = session.getAttributes().get("userId");
        if (userIdObj instanceof Number userId) {
            return String.valueOf(userId.longValue());
        } else if (userIdObj instanceof String userId) {
            return userId;
        }
        return null;
    }

    private void handleClientMessage(String connectionId, String userId, String rawMessage) {
        try {
            // 1️⃣ 安全校验：防止空消息/非法用户
            if (StringUtils.isEmpty(rawMessage) || userId == null) {
                log.warn("⚠️ 无效消息参数 | connId={}, userId={}", connectionId, userId);
                return;
            }
            // 2️⃣ 解析客户端消息（假设为标准JSON格式）
            ChatMessage msg = gson.fromJson(rawMessage, ChatMessage.class);
            if (msg == null) {
                log.warn("⚠️ 消息内容为空 | userId={}", userId);
                return;
            }
            if ("query_online".equalsIgnoreCase(msg.getType())) {
                handleQueryOnlineStatus(connectionId, userId, msg);
                return;
            }

            if ("subscribe_status".equalsIgnoreCase(msg.getType())) {
                handleSubscribeStatus(connectionId, userId, msg);
                return;
            }
            if (StringUtils.isEmpty(msg.getContent())) {
                log.warn("⚠️ 消息内容为空 | userId={}", userId);
                return;
            }
            // 3️⃣ 【关键】覆盖from字段，防止客户端伪造身份（安全加固）
            msg.setFrom(userId); // 强制使用连接认证的userId
            msg.setTimestamp(LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()); // 服务端统一时间戳
            // 4️⃣ 业务层处理（持久化/风控/通知等）
//            messageService.saveMessage(msg); // 假设已注入messageService
            // 5️⃣ 消息分发逻辑
            TextMessage textMsg = new TextMessage(gson.toJson(msg));

            if ("-1".equals(msg.getTo()) || "all".equalsIgnoreCase(msg.getTo())) {
                // 🌐 群聊：广播给所有在线用户（含自己）
                sendMessageToAll(textMsg);
                log.debug("📤 群发消息 | from={}, content={}", userId, msg.getContent());
            } else {
                // 💬 私聊：精准投递（含发送者回显）
                // 发送给自己（回显，提升体验）
                sendMessageToUser(userId, textMsg.getPayload());
                // 发送给目标用户
                sendMessageToUser(msg.getTo(), textMsg.getPayload());
                log.info("📤 私聊投递 | from={} → to={}, content={}", userId, msg.getTo(), msg.getContent());
            }
        } catch (JsonSyntaxException e) {
            log.error("❌ JSON解析失败 | rawMessage={}", rawMessage, e);
            sendSystemMessage(connectionId, "消息格式错误，请重试");
        } catch (Exception e) {
            log.error("❌ 消息处理异常 | userId={}, connId={}", userId, connectionId, e);
            // 可选：向客户端返回通用错误
            sendSystemMessage(connectionId, "服务端处理异常，请稍后重试");
        }
    }

// ===== 辅助方法（需在类中实现）=====

    /**
     * 发送系统通知消息（避免阻塞主流程）
     */
    private void sendSystemMessage(String connectionId, String content) {
        try {
            ChatMessage sysMsg = new ChatMessage();
            sysMsg.setType(SYSTEM);
            sysMsg.setContent(content);
            sysMsg.setFrom(SYSTEM);
            sysMsg.setTo(SYSTEM);
            sysMsg.setTimestamp(LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
            ConnectionInfo info = localConnections.get(connectionId); // 假设存在connectionId→Session映射
            if (info != null && info.session().isOpen()) {
                info.session().send(Mono.just(info.session().textMessage(content)))
                        .onErrorResume(e -> {
                            log.warn("⚠️ Broadcast delivery failed to user: {}", info.userId(), e);
                            return Mono.empty();
                        })
                        .subscribe();
            }
        } catch (Exception e) {
            log.warn("⚠️ 系统消息发送失败", e);
        }
    }

    private void handleQueryOnlineStatus(String connectionId, String currentUserId, ChatMessage requestMsg) {
        try {
            String targetUserId = requestMsg.getTo();
            if (StringUtils.isEmpty(targetUserId)) {
                sendSystemMessage(connectionId, "查询在线状态需要指定目标用户ID");
                return;
            }
            String wsUserKey = WS_USER + targetUserId;
            hashOps.entries(wsUserKey)
                    .collectMap(Map.Entry::getKey, Map.Entry::getValue)
                    .defaultIfEmpty(Collections.emptyMap())
                    .map(connections -> {
                        boolean isOnline = !connections.isEmpty();
                        ChatMessage response = new ChatMessage();
                        response.setType("online_status");
                        response.setFrom(SYSTEM);
                        response.setTo(currentUserId);
                        response.setContent(targetUserId);
                        response.setTimestamp(LocalDateTime.now()
                                .atZone(ZoneId.systemDefault())
                                .toInstant().toEpochMilli());
                        if (isOnline) {
                            response.setStatus("online");
                            response.setExtraData("{\"message\":\"用户在线\"}");
                        } else {
                            response.setStatus("offline");
                            response.setExtraData("{\"message\":\"用户离线\"}");
                        }
                        return gson.toJson(response);
                    })
                    .doOnNext(json -> {
                        ConnectionInfo info = localConnections.get(connectionId);
                        if (info != null && info.session().isOpen()) {
                            info.session().send(Mono.just(info.session().textMessage(json)))
                                    .onErrorResume(e -> {
                                        log.warn("⚠️ 发送在线状态响应失败 | connId: {}", connectionId, e);
                                        return Mono.empty();
                                    })
                                    .subscribe();
                        }
                    })
                    .doOnError(e -> {
                        log.error("❌ 查询在线状态失败 | targetUserId: {}", targetUserId, e);
                        sendSystemMessage(connectionId, "查询在线状态失败");
                    })
                    .subscribe();

            log.info("🔍 查询在线状态 | requester: {} | target: {}", currentUserId, targetUserId);
        } catch (Exception e) {
            log.error("❌ 处理在线状态查询异常 | connId: {}", connectionId, e);
            sendSystemMessage(connectionId, "查询在线状态异常");
        }
    }

    private void handleSubscribeStatus(String connectionId, String currentUserId, ChatMessage requestMsg) {
        try {
            String extraData = requestMsg.getExtraData();
            if (StringUtils.isEmpty(extraData)) {
                sendSystemMessage(connectionId, "订阅状态需要指定目标用户列表");
                return;
            }
            Map<?, ?> data = gson.fromJson(extraData, Map.class);
            Object targetUserIdsObj = data.get("targetUserIds");

            if (!(targetUserIdsObj instanceof List)) {
                sendSystemMessage(connectionId, "目标用户列表格式错误");
                return;
            }
            List<String> targetUserIds = (List<String>) targetUserIdsObj;
            String subscribeKey = "ws:subscribe:" + currentUserId;
            Map<String, String> subscribeData = targetUserIds.stream()
                    .collect(Collectors.toMap(
                            id -> id,
                            _ -> System.currentTimeMillis() + ""
                    ));
            hashOps.putAll(subscribeKey, subscribeData)
                    .then(reactiveRedisTemplate.expire(subscribeKey, ofMinutes(10)))
                    .doOnSuccess(v -> {
                        log.info("✅ 用户 {} 订阅了 {} 个用户的状态变化", currentUserId, targetUserIds.size());
                        ChatMessage response = new ChatMessage();
                        response.setType("subscribe_success");
                        response.setFrom(SYSTEM);
                        response.setTo(currentUserId);
                        response.setContent("订阅成功");
                        response.setTimestamp(LocalDateTime.now()
                                .atZone(ZoneId.systemDefault())
                                .toInstant().toEpochMilli());
                        ConnectionInfo info = localConnections.get(connectionId);
                        if (info != null && info.session().isOpen()) {
                            info.session().send(Mono.just(info.session().textMessage(gson.toJson(response))))
                                    .subscribe();
                        }
                    })
                    .doOnError(e -> {
                        log.error("❌ 订阅状态失败", e);
                        sendSystemMessage(connectionId, "订阅状态失败");
                    })
                    .subscribe();

        } catch (Exception e) {
            log.error("❌ 处理订阅状态异常 | connId: {}", connectionId, e);
            sendSystemMessage(connectionId, "订阅状态异常");
        }
    }

    private void notifySubscribers(String changedUserId, boolean isOnline) {
        String wsUserKey = WS_USER + changedUserId;
        hashOps.entries(wsUserKey)
                .collectMap(Map.Entry::getKey, Map.Entry::getValue)
                .defaultIfEmpty(Collections.emptyMap())
                .subscribe(connections -> {
                    if (connections.isEmpty()) {
                        return;
                    }
                    ChatMessage notification = new ChatMessage();
                    notification.setType("status_change");
                    notification.setFrom(SYSTEM);
                    notification.setContent(changedUserId);
                    notification.setStatus(isOnline ? "online" : "offline");
                    notification.setTimestamp(LocalDateTime.now()
                            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
                    String payload = gson.toJson(notification);
                    connections.forEach((connId, targetServer) -> {
                        if (targetServer.equals(serverId)) {
                            ConnectionInfo info = localConnections.get(connId);
                            if (info != null && info.session().isOpen()) {
                                info.session().send(Mono.just(info.session().textMessage(payload)))
                                        .subscribe();
                            }
                        } else {
                            String channel = "ws:msg:" + targetServer;
                            Map<String, String> routedMsg = Map.of(
                                    "connectionId", connId,
                                    "payload", payload
                            );
                            try {
                                String jsonMsg = objectMapper.writeValueAsString(routedMsg);
                                reactiveRedisTemplate.convertAndSend(channel, jsonMsg).subscribe();
                            } catch (Exception e) {
                                log.error("❌ 路由状态通知失败", e);
                            }
                        }
                    });
                });
    }

    /**
     * 完整版广播方法（生产环境推荐）
     *
     * @param textMessage         要发送的文本消息
     * @param excludeConnectionId 可选：排除指定连接
     * @param isGlobal            true=全局广播（跨实例），false=仅本地广播
     */
    private void sendMessageToAll(TextMessage textMessage, String excludeConnectionId, boolean isGlobal) {
        if (textMessage == null || StringUtils.isEmpty(textMessage.getPayload())) {
            log.warn("⚠️ 消息为空，取消广播");
            return;
        }
        String payload = textMessage.getPayload();
        long broadcastId = System.currentTimeMillis();
        int localCount = localConnections.size();
        log.info("📤 广播开始 | broadcastId: {} | 方式: {} | 本地连接数: {} | 排除: {}",
                broadcastId,
                isGlobal ? "全局(Redis)" : "本地",
                localCount,
                excludeConnectionId);
        long startTime = System.currentTimeMillis();
        AtomicInteger deliveredCount = new AtomicInteger(0);
        AtomicInteger failedCount = new AtomicInteger(0);
        if (isGlobal) {
            // 全局广播：通过 Redis Pub/Sub
            broadcastMessage(payload);
        } else {
            // 仅本地广播
            for (Map.Entry<String, ConnectionInfo> entry : localConnections.entrySet()) {
                String connId = entry.getKey();
                ConnectionInfo info = entry.getValue();
                // 排除指定连接
                if (excludeConnectionId != null && excludeConnectionId.equals(connId)) {
                    continue;
                }
                if (info.session().isOpen()) {
                    info.session().send(Mono.just(info.session().textMessage(payload)))
                            .doOnSuccess(v -> deliveredCount.incrementAndGet())
                            .doOnError(e -> {
                                failedCount.incrementAndGet();
                                log.warn("⚠️ 广播发送失败 | connId: {} | userId: {}",
                                        connId, info.userId(), e);
                            })
                            .subscribe();
                }
            }
        }
        long costTime = System.currentTimeMillis() - startTime;
        log.info("✅ 广播完成 | broadcastId: {} | 成功: {} | 失败: {} | 耗时: {}ms",
                broadcastId, deliveredCount.get(), failedCount.get(), costTime);
    }

    /**
     * 重载：全局广播（默认）
     */
    private void sendMessageToAll(TextMessage textMessage) {
        sendMessageToAll(textMessage, null, true);
    }

    /**
     * 重载：全局广播 + 排除
     */
    private void sendMessageToAll(TextMessage textMessage, String excludeConnectionId) {
        sendMessageToAll(textMessage, excludeConnectionId, true);
    }

    /**
     * 重载：本地广播
     */
    private void sendMessageToAllLocal(TextMessage textMessage, String excludeConnectionId) {
        sendMessageToAll(textMessage, excludeConnectionId, false);
    }

    /**
     * 重载：直接传入字符串（全局）
     */
    private void sendMessageToAll(String payload) {
        sendMessageToAll(new TextMessage(payload), null, true);
    }

    /**
     * 重载：直接传入字符串 + 排除（全局）
     */
    private void sendMessageToAll(String payload, String excludeConnectionId) {
        sendMessageToAll(new TextMessage(payload), excludeConnectionId, true);
    }

    private Mono<Void> updateOnlineStatus(String userId, boolean online) {
        String key = "user:online:" + userId;
        return (online
                ? valueOps.set(key, "1", ofMinutes(5)).then()
                : reactiveRedisTemplate.delete(key).then())
                .doOnSuccess(v -> notifySubscribers(userId, online));
    }

    private void cleanupConnection(String connectionId, String userId) {
        // 1. 移除本地连接
        localConnections.remove(connectionId);
        // 2. 清理 Redis 数据（异步执行，避免阻塞）
        String userKey = WS_USER + userId;
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
    private record ConnectionInfo(WebSocketSession session, String userId) {
    }

    // ==================== 诊断方法（可选） ====================

    public int getLocalConnectionCount() {
        return localConnections.size();
    }

}
