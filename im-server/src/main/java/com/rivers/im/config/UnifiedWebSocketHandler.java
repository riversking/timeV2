package com.rivers.im.config;

import com.rivers.im.context.ConnectionContext;
import com.rivers.im.manage.LocalSessionManager;
import com.rivers.im.record.WsEnvelope;
import com.rivers.im.router.TopicHandler;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.ReactiveSubscription;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@Slf4j
public class UnifiedWebSocketHandler implements WebSocketHandler {

    private final Map<String, TopicHandler> routerMap;
    private final ObjectMapper objectMapper;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final ReactiveRedisMessageListenerContainer listenerContainer;
    private final LocalSessionManager sessionManager;

    @Value("${spring.cloud.client.hostname:127.0.0.1}:${server.port:8080}")
    private String currentServerId;

    public UnifiedWebSocketHandler(List<TopicHandler> handlers,
                                   ObjectMapper objectMapper,
                                   ReactiveStringRedisTemplate redisTemplate,
                                   ReactiveRedisMessageListenerContainer listenerContainer,
                                   LocalSessionManager sessionManager) {
        this.routerMap = handlers.stream().collect(Collectors.toMap(TopicHandler::getTopic, Function.identity()));
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
        this.listenerContainer = listenerContainer;
        this.sessionManager = sessionManager;
        log.info("🚀 网关注册 Topics: {}", routerMap.keySet());
    }

    @PostConstruct
    public void init() {
        String channel = "ws:node:" + currentServerId;
        listenerContainer.receive(ChannelTopic.of(channel))
                .map(ReactiveSubscription.Message::getMessage)
                .subscribe(this::handleCrossServerMessage,
                        e -> log.error("Redis Pub/Sub 监听异常", e));
        log.info("📡 节点 [{}] 已订阅跨服频道", currentServerId);
    }

    @PreDestroy
    public void destroy() {
        log.info("🧹 网关节点关闭，清理本地连接...");
    }

    @Override
    @NullMarked
    public Mono<Void> handle(WebSocketSession session) {
        String connId = UUID.randomUUID().toString();
        String userId = extractUserId(session);
        if (userId == null) {
            return session.close();
        }
        // 1. 初始化上下文并注册到 Manager
        ConnectionContext ctx = new ConnectionContext(session, userId);
        sessionManager.register(connId, ctx);
        // 2. Redis 分布式注册
        String routeKey = "ws:route:" + userId;
        Mono<Void> register = redisTemplate.opsForHash().put(routeKey, connId, currentServerId)
                .then(redisTemplate.expire(routeKey, Duration.ofMinutes(5)))
                .then();
        // 3. 出站流 (从 Sinks 读取并发送给客户端)
        Mono<Void> output = session.send(ctx.getOutboundSink().asFlux().map(session::textMessage));
        // 4. 入站流 (接收、拆包、路由)
        Mono<Void> input = session.receive()
                .map(msg -> msg.getPayloadAsText())
                .flatMap(raw -> dispatchMessage(userId, connId, raw))
                .then();

        // 5. 心跳续期
        Flux<Void> heartbeat = Flux.interval(Duration.ofSeconds(25))
                .takeWhile(_ -> session.isOpen())
                .flatMap(_ -> redisTemplate.expire(routeKey, Duration.ofMinutes(5)).then())
                .onErrorContinue((e, _) -> {
                });

        // 6. 合并流，任意一个结束则触发清理
        return Mono.when(register, input, output, heartbeat.then())
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
        redisTemplate.opsForHash().remove(routeKey, connId).subscribe();
        log.info("🧹 连接清理: {} | user: {}", connId, userId);
    }

    private String extractUserId(WebSocketSession session) {
        // 生产环境从 Header/Token 获取，这里兼容 URL 参数测试
        String query = session.getHandshakeInfo().getUri().getQuery();
        if (query != null && query.contains("userId=")) {
            return query.split("userId=")[1].split("&")[0];
        }
        Object obj = session.getAttributes().get("userId");
        return obj != null ? obj.toString() : null;
    }
}