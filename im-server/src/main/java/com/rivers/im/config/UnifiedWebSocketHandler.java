package com.rivers.im.config;

import com.rivers.im.constant.CrossServerChannel;
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
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.connection.stream.Record;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
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
    private final LocalSessionManager sessionManager;
    private final String currentServerId;
    /**
     * 消费组内消费者名：serverId + 随机后缀，构造时生成、进程内稳定。
     * 每次启动都是新消费者，旧消费者的滞留消息由 reclaimStale 回收。
     */
    private final String consumerName;

    @Nullable
    private Disposable crossServerSubscription;

    public UnifiedWebSocketHandler(List<TopicHandler> handlers,
                                   ObjectMapper objectMapper,
                                   ReactiveStringRedisTemplate redisTemplate,
                                   LocalSessionManager sessionManager,
                                   @Value("${spring.cloud.client.hostname:127.0.0.1}:${server.port:8080}")
                                   String currentServerId) {
        this.routerMap = handlers.stream()
                .collect(Collectors.toMap(TopicHandler::getTopic, Function.identity()));
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
        this.sessionManager = sessionManager;
        this.currentServerId = currentServerId;
        this.consumerName = currentServerId + ":" + UUID.randomUUID().toString().substring(0, 8);
        log.info("🚀 网关注册 Topics: {}", routerMap.keySet());
    }

    @PostConstruct
    public void init() {
        String streamKey = CrossServerChannel.streamKeyOf(currentServerId);
        crossServerSubscription = redisTemplate.opsForStream()
                .createGroup(streamKey, ReadOffset.from("0-0"), CrossServerChannel.GROUP_NAME)
                .onErrorResume(e -> {
                    // 消费组已存在（多副本/重启场景）视为正常。
                    // 必须检查完整异常链：Spring Data 把 Redis 错误包成 "Error in execution"，
                    // BUSYGROUP 真实原因在 cause 里，只查顶层消息会误判成致命错误。
                    if (isBusyGroupError(e)) {
                        return Mono.just("EXISTS");
                    }
                    return Mono.error(e);
                })
                .then(reclaimStale(streamKey))
                .thenMany(consumeLoop(streamKey))
                .retryWhen(Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(1))
                        .maxBackoff(Duration.ofSeconds(30))
                        .doBeforeRetry(rs -> log.warn("🔁 Redis Stream 消费重连: attempt={}, error={}",
                                rs.totalRetries() + 1, rootCauseMessage(rs.failure()))))
                .subscribe(
                        v -> {
                        },
                        e -> log.error("❌ Redis Stream 消费循环终止，跨服消息通道中断: {}",
                                rootCauseMessage(e))
                );
        log.info("📡 节点 [{}] 已加入跨服流消费组, consumer={}", currentServerId, consumerName);
    }

    @PreDestroy
    public void destroy() {
        if (crossServerSubscription != null && !crossServerSubscription.isDisposed()) {
            crossServerSubscription.dispose();
            log.info("🧹 已停止跨服流消费: {}", currentServerId);
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
        return Mono.when(List.of(register, input, output, heartbeat.then()))
                .doOnError(e -> log.error("❌ WebSocket 流异常: userId={}, connId={}", userId, connId, e))
                .doFinally(sig -> cleanup(connId, userId));
    }

    private Mono<Void> dispatchMessage(String userId, String connId, String raw) {
        // 预检空消息体；Jackson 对字面量 "null" 会解析出 null，提前拦截保持语义
        if (StringUtils.isBlank(raw) || "null".equals(raw.trim())) {
            return sendErrorResult(connId, "消息格式不合法: 空消息体");
        }
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
    private Mono<Void> sendErrorResult(String connId, @Nullable String message) {
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

    /**
     * 无限消费循环：BLOCK 1s 读一批（空闲时天然节流），处理后批量 ACK。
     */
    private Flux<Void> consumeLoop(String streamKey) {
        return Flux.defer(() -> readAndAckBatch(streamKey)).repeat();
    }

    /**
     * 消费组模式读一批并批量 ACK。
     * 单条处理失败也照常 ACK，避免毒消息无限重投。
     */
    private Mono<Void> readAndAckBatch(String streamKey) {
        // 显式具体类型数组：避免 varargs 隐式创建 StreamOffset<K>[] 泛型数组告警
        StreamOffset<String>[] offsets = arrayOf(StreamOffset.create(streamKey, ReadOffset.lastConsumed()));
        // BLOCK 必须小于 Lettuce 命令超时（本环境 3s）：空闲时 XREADGROUP 会阻塞 BLOCK 时长，
        // 超过命令超时就被杀掉报 "Command timed out"，这里用 1s 留足余量
        return redisTemplate.opsForStream()
                .read(Consumer.from(CrossServerChannel.GROUP_NAME, consumerName),
                        StreamReadOptions.empty().count(50).block(Duration.ofSeconds(1)),
                        offsets)
                .concatMap(this::handleStreamRecord)
                .collectList()
                .flatMap(ids -> {
                    if (ids.isEmpty()) {
                        return Mono.empty();
                    }
                    // 显式具体类型数组：避免 IntFunction<T[]> 泛型重载的隐式数组创建告警
                    RecordId[] recordIds = ids.toArray(new RecordId[0]);
                    return redisTemplate.opsForStream()
                            .acknowledge(streamKey, CrossServerChannel.GROUP_NAME, recordIds)
                            .doOnNext(n -> log.debug("📥 Stream ACK: key={}, count={}", streamKey, n))
                            .then();
                });
    }

    private Mono<RecordId> handleStreamRecord(MapRecord<String, Object, Object> streamRecord) {
        return Mono.fromRunnable(() -> {
                    try {
                        Map<Object, Object> fields = streamRecord.getValue();
                        Object connIdObj = fields.get("connId");
                        Object payloadObj = fields.get("payload");
                        // 运行时实际为 String（ReactiveStringRedisTemplate 的 hash 序列化器是 String）
                        String connId = connIdObj != null ? connIdObj.toString() : null;
                        String payload = payloadObj != null ? payloadObj.toString() : null;
                        if (connId != null && payload != null) {
                            sessionManager.pushToLocal(connId, payload);
                        } else {
                            log.warn("⚠️ 跨服消息字段缺失: record={}", streamRecord.getId());
                        }
                    } catch (Exception e) {
                        log.error("❌ 跨服消息处理失败, record={}", streamRecord.getId(), e);
                    }
                })
                .thenReturn(streamRecord.getId());
    }

    /**
     * 死消费者滞留清理：XRANGE 扫全流 + XACK 清消费组 PEL。
     * XACK 只移除 PEL 中的滞留消息（原消费者已死，对应连接不存在，不再投递）；
     * 未投递过的消息不在 PEL，不受影响，仍由 ">" 正常补投。
     * best-effort：失败只告警，不影响消费主循环。
     */
    private Mono<Void> reclaimStale(String streamKey) {
        return redisTemplate.opsForStream()
                .range(streamKey, Range.unbounded())
                .map(Record::getId)
                .collectList()
                .flatMap(ids -> {
                    if (ids.isEmpty()) {
                        return Mono.empty();
                    }
                    return redisTemplate.opsForStream()
                            .acknowledge(streamKey, CrossServerChannel.GROUP_NAME,
                                    ids.toArray(new RecordId[0]))
                            .doOnSuccess(acked -> log.info("🧹 清理滞留 PEL: key={}, acked={}, scanned={}",
                                    streamKey, acked, ids.size()))
                            .then();
                })
                .onErrorResume(e -> {
                    log.warn("⚠️ 滞留消息清理失败（不影响主流程）: {}", e.getMessage());
                    return Mono.empty();
                });
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

    /**
     * 泛型 varargs 安全助手：把隐式泛型数组创建收敛到此处并声明安全。
     * 调用方拿到具体类型数组后透传给 varargs 参数，不再触发 unchecked 告警。
     */
    @SafeVarargs
    private static <T> T[] arrayOf(T... elements) {
        return elements;
    }

    /**
     * 沿 cause 链逐层查找 BUSYGROUP（XGROUP CREATE 消费组已存在的正常场景）。
     */
    private boolean isBusyGroupError(Throwable t) {
        Throwable cur = t;
        while (cur != null) {
            if (cur.getMessage() != null && cur.getMessage().contains("BUSYGROUP")) {
                return true;
            }
            cur = cur.getCause();
        }
        return false;
    }

    /**
     * 取最深 cause 的消息：Spring Data 顶层只有 "Error in execution"，
     * 真实 Redis 报错（NOGROUP/WRONGTYPE/BUSYGROUP 等）在链尾。
     */
    private String rootCauseMessage(@Nullable Throwable t) {
        Throwable cur = t;
        while (cur != null && cur.getCause() != null) {
            cur = cur.getCause();
        }
        return cur != null && cur.getMessage() != null ? cur.getMessage() : "unknown";
    }
}