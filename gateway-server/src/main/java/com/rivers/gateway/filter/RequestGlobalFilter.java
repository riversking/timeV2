package com.rivers.gateway.filter;

import com.rivers.core.config.FilterIgnorePropertiesConfig;
import com.rivers.core.entity.LoginUser;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 网关统一鉴权 — 纯 Session Bearer 模式（无 JWT / 无 Lua）
 * <p>
 * session:{sid}                  TTL=30d → {userId, username, familyId, createdAt, rotateAt, prevSid}
 * session:family:{familyId}      TTL=30d → 当前有效 sid（家族指针）
 * rotated:{oldSid}               TTL=30d → 墓碑，旧 sid 重放检测
 * session:last:{sid}             TTL=2d  → 活跃窗口，每请求续期；未命中视为不活跃登出
 * session:grace:{sid}:{oldSid}   TTL=2d  → 宽限额度，SETNX 消费一次
 * <p>
 * 轮换：每 12h（rotateAt 到期）以"墓碑 SETNX"抢占轮换权，
 * 新 sid 通过响应头 X-New-Session 下发；旧 sid 重放 → 宽限一次或整族吊销。
 *
 * @author riversking
 */
@Component
@Slf4j
@NullMarked
public class RequestGlobalFilter implements WebFilter, Ordered {

    private static final String CODE_401 = "{\"code\":401,\"msg\":\"鉴权失败\"}";
    private static final String SESSION_PREFIX = "session:";
    private static final String ACTIVE_PREFIX = "session:last:";
    private static final String FAMILY_PREFIX = "session:family:";
    private static final String ROTATED_PREFIX = "rotated:";
    private static final String GRACE_PREFIX = "session:grace:";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String ACTIVE_VALUE = "1";
    private static final String NEW_SESSION_HEADER = "X-New-Session";
    private static final Duration ACTIVE_TTL = Duration.ofDays(2);
    private static final Duration SESSION_TTL = Duration.ofDays(30);
    private static final long ROTATE_INTERVAL_MILLIS = Duration.ofHours(12).toMillis();
    private static final String ATTR_LOGIN_USER = "gateway.loginUser";
    public static final String LOGIN_USER = "loginUser";
    private static final int MAX_BODY_BUFFER_SIZE = 1024 * 1024;

    private final FilterIgnorePropertiesConfig filterIgnorePropertiesConfig;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RequestGlobalFilter(FilterIgnorePropertiesConfig filterIgnorePropertiesConfig,
                               ReactiveStringRedisTemplate redisTemplate,
                               ObjectMapper objectMapper) {
        this.filterIgnorePropertiesConfig = filterIgnorePropertiesConfig;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    // ═══════════════════════════════════════════════════════════════
    //  主入口
    // ═══════════════════════════════════════════════════════════════
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        var request = exchange.getRequest();
        var path = request.getPath().value();
        if (filterIgnorePropertiesConfig.getUrls().stream()
                .anyMatch(pattern -> pathMatcher.match(pattern, path))) {
            return chain.filter(exchange);
        }
        var sessionId = extractSessionId(request);
        if (sessionId == null) {
            return clearSessionAnd401(exchange, null);
        }
        return loadValidUser(exchange, sessionId)
                .switchIfEmpty(Mono.defer(() -> reject401(exchange, sessionId)))
                .flatMap(user -> forwardWithIdentity(exchange, chain, user));
    }

    private @Nullable String extractSessionId(ServerHttpRequest request) {
        var auth = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (auth == null || auth.length() <= BEARER_PREFIX.length()
                || !auth.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return null;
        }
        var sessionId = auth.substring(BEARER_PREFIX.length());
        return StringUtils.hasText(sessionId) ? sessionId : null;
    }

    // ═══════════════════════════════════════════════════════════════
    //  异步语义层1：墓碑检测 → 活跃窗口 → session → 到期轮换
    //  任何环节失败返回 Mono.empty()，由调用方 switchIfEmpty 统一兜底 401
    // ═══════════════════════════════════════════════════════════════
    private Mono<LoginUser> loadValidUser(ServerWebExchange exchange, String sessionId) {
        // 第一步：墓碑检测。旧 sid 重放 → 宽限一次或整族吊销
        return redisTemplate.opsForValue().get(ROTATED_PREFIX + sessionId)
                .filter(StringUtils::hasText)
                .flatMap(familyId -> handleReplay(exchange, sessionId, familyId))
                .switchIfEmpty(Mono.defer(() -> loadActiveSession(exchange, sessionId)));
    }

    /**
     * 旧 sid 重放判定（无 Lua）：
     * prevSid 不匹配 → 盗用，整族吊销；
     * prevSid 匹配 → 用 SETNX 消费宽限额度：成功补发当前 sid，失败判定盗用
     */
    private Mono<LoginUser> handleReplay(ServerWebExchange exchange, String oldSid, String familyId) {
        return redisTemplate.opsForValue().get(FAMILY_PREFIX + familyId)
                .filter(StringUtils::hasText)
                .flatMap(currentSid -> loadSessionInfo(currentSid)
                        .flatMap(info -> {
                            if (!oldSid.equals(info.prevSid())) {
                                return killFamily(familyId, currentSid)
                                        .then(Mono.<LoginUser>empty());
                            }
                            var graceKey = GRACE_PREFIX + currentSid + ":" + oldSid;
                            return redisTemplate.opsForValue()
                                    .setIfAbsent(graceKey, "1", ACTIVE_TTL)
                                    .flatMap(granted -> {
                                        if (!Boolean.TRUE.equals(granted)) {
                                            return killFamily(familyId, currentSid)
                                                    .then(Mono.<LoginUser>empty());
                                        }
                                        exchange.getResponse().getHeaders()
                                                .set(NEW_SESSION_HEADER, currentSid);
                                        log.warn("旧会话宽限补发: oldSid={} -> currentSid={}",
                                                oldSid, currentSid);
                                        return Mono.just(info.loginUser());
                                    });
                        }));
    }

    /**
     * 整族吊销：删当前 session、活跃窗口、家族指针。
     * 家族内其他 sid 因指针失效，无法再通过轮换/宽限路径续命
     */
    private Mono<Void> killFamily(String familyId, String currentSid) {
        log.warn("检测到旧会话重放，整族吊销: familyId={}", familyId);
        return redisTemplate.delete(
                SESSION_PREFIX + currentSid,
                ACTIVE_PREFIX + currentSid,
                FAMILY_PREFIX + familyId).then();
    }

    /**
     * 正常路径：活跃窗口（2 天不活跃即失效）→ 读取 session → rotateAt 到期则轮换
     */
    private Mono<LoginUser> loadActiveSession(ServerWebExchange exchange, String sessionId) {
        return redisTemplate.opsForValue().get(ACTIVE_PREFIX + sessionId)
                .filter(StringUtils::hasText)
                .flatMap(active -> redisTemplate.opsForValue()
                        .set(ACTIVE_PREFIX + sessionId, ACTIVE_VALUE, ACTIVE_TTL)
                        .then(loadSessionInfo(sessionId)))
                .flatMap(info -> maybeRotate(exchange, sessionId, info));
    }

    private Mono<SessionInfo> loadSessionInfo(String sessionId) {
        return redisTemplate.opsForValue().get(SESSION_PREFIX + sessionId)
                .filter(StringUtils::hasText)
                .flatMap(json -> Mono.fromCallable(
                                () -> objectMapper.readValue(json, SessionInfo.class))
                        .subscribeOn(Schedulers.boundedElastic())
                        .onErrorResume(e -> Mono.empty()));
    }

    /**
     * 轮换（无 Lua）：先建新、再 SETNX 墓碑抢占、后删旧。
     * 墓碑 SETNX 是唯一原子切换点：
     * - 切换点之前崩溃 → oldSid 无感，最多留孤儿新 sid 自愈；
     * - 切换点之后崩溃 → 旧 sid 走宽限路径补发新 sid；
     * - 抢占失败 → 并发已轮换，清理自己的孤儿新 sid 后直接放行（身份相同）
     */
    private Mono<LoginUser> maybeRotate(ServerWebExchange exchange, String sessionId, SessionInfo info) {
        if (System.currentTimeMillis() < info.rotateAt()) {
            return Mono.just(info.loginUser());
        }
        var newSid = UUID.randomUUID().toString();
        var next = info.rotate(sessionId);
        return Mono.fromCallable(() -> objectMapper.writeValueAsString(next))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(json -> redisTemplate.opsForValue()
                        .set(SESSION_PREFIX + newSid, json, SESSION_TTL)
                        .then(redisTemplate.opsForValue()
                                .set(FAMILY_PREFIX + info.familyId(), newSid, SESSION_TTL))
                        .then(redisTemplate.opsForValue()
                                .set(ACTIVE_PREFIX + newSid, ACTIVE_VALUE, ACTIVE_TTL))
                        .then(redisTemplate.opsForValue()
                                .setIfAbsent(ROTATED_PREFIX + sessionId,
                                        info.familyId(), SESSION_TTL))
                        .flatMap(acquired -> {
                            if (!Boolean.TRUE.equals(acquired)) {
                                return redisTemplate.delete(
                                                SESSION_PREFIX + newSid, ACTIVE_PREFIX + newSid)
                                        .then(Mono.just(info.loginUser()));
                            }
                            return redisTemplate.delete(
                                            SESSION_PREFIX + sessionId, ACTIVE_PREFIX + sessionId)
                                    .then(Mono.fromRunnable(() -> exchange.getResponse()
                                            .getHeaders().set(NEW_SESSION_HEADER, newSid)))
                                    .thenReturn(info.loginUser());
                        }));
    }

    // ═══════════════════════════════════════════════════════════════
    //  异步语义层2：注入身份 + 构建请求 + 转发
    // ═══════════════════════════════════════════════════════════════
    private Mono<Void> forwardWithIdentity(ServerWebExchange exchange,
                                           WebFilterChain chain,
                                           LoginUser loginUser) {
        exchange.getAttributes().put(ATTR_LOGIN_USER, loginUser);
        var mutatedRequest = exchange.getRequest().mutate()
                .header("X-User-Id", loginUser.getUserId())
                .build();
        var finalRequest = mutatedRequest.getMethod() == HttpMethod.GET
                ? handleGetRequest(mutatedRequest, loginUser.getUserId())
                : new BodyRewriteDecorator(mutatedRequest, exchange);
        return chain.filter(exchange.mutate().request(finalRequest).build());
    }

    /**
     * GET 请求：userId 拼入 Query String（replaceQueryParam 自动去重与编码）
     */
    private ServerHttpRequest handleGetRequest(ServerHttpRequest request, String userId) {
        var uri = UriComponentsBuilder.fromUri(request.getURI())
                .replaceQueryParam("userId", userId)
                .build(true).toUri();
        return request.mutate().uri(uri).build();
    }

    // ═══════════════════════════════════════════════════════════════
    //  401 + 清除会话
    // ═══════════════════════════════════════════════════════════════
    private Mono<Void> clearSessionAnd401(ServerWebExchange exchange, @Nullable String sessionId) {
        var response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().setCacheControl(CacheControl.noStore().getHeaderValue());
        var buffer = response.bufferFactory().wrap(CODE_401.getBytes(StandardCharsets.UTF_8));
        var cleanup = StringUtils.hasText(sessionId)
                ? redisTemplate.delete(SESSION_PREFIX + sessionId, ACTIVE_PREFIX + sessionId).then()
                : Mono.empty();
        return cleanup.then(response.writeWith(Mono.just(buffer)));
    }

    private Mono<LoginUser> reject401(ServerWebExchange exchange, String sessionId) {
        return clearSessionAnd401(exchange, sessionId).then(Mono.empty());
    }

    // ═══════════════════════════════════════════════════════════════
    //  辅助
    // ═══════════════════════════════════════════════════════════════
    @Override
    public int getOrder() {
        return -1000;
    }

    /**
     * 会话信息：直接存 Redis，JWT 不参与认证。
     * 宽限额度不在 JSON 中，由独立 key（session:grace:...）SETNX 消费
     */
    private record SessionInfo(String userId, String username, String familyId,
                               long createdAt, long rotateAt, String prevSid) {
        LoginUser loginUser() {
            var u = new LoginUser();
            u.setUserId(userId);
            u.setUsername(username);
            return u;
        }

        SessionInfo rotate(String prevSid) {
            return new SessionInfo(userId, username, familyId, createdAt,
                    System.currentTimeMillis() + ROTATE_INTERVAL_MILLIS, prevSid);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Body 重写装饰器
    //  JSON 预检 + Content-Type 过滤 + OOM 双重防护 + 空 Body 降级 + 单次缓冲
    // ═══════════════════════════════════════════════════════════════
    private class BodyRewriteDecorator extends ServerHttpRequestDecorator {

        private final ServerWebExchange exchange;
        private final AtomicReference<@Nullable Mono<byte[]>> rewrittenBody = new AtomicReference<>();

        BodyRewriteDecorator(ServerHttpRequest delegate, ServerWebExchange exchange) {
            super(delegate);
            this.exchange = exchange;
        }

        @Override
        public Flux<DataBuffer> getBody() {
            var loginUser = exchange.getAttribute(ATTR_LOGIN_USER);
            if (loginUser == null) {
                return super.getBody();
            }
            // 预检：非 JSON 直接透传，不做任何缓冲
            var contentType = getHeaders().getContentType();
            if (!isJsonContentType(contentType)) {
                return super.getBody();
            }
            // 预检：Content-Length 超过阈值直接透传，防止 OOM
            if (getHeaders().getContentLength() > MAX_BODY_BUFFER_SIZE) {
                log.warn("Body size {} exceeds limit {}, skip loginUser injection for {}",
                        getHeaders().getContentLength(), MAX_BODY_BUFFER_SIZE, getURI().getPath());
                return super.getBody();
            }
            // 只缓冲一次：getBody() 可能被多次调用，重复 join 会读到空 body。
            // AtomicReference 懒加载保证并发安全；竞态中未中标的 Mono 不会被订阅，无副作用
            var cached = rewrittenBody.updateAndGet(current ->
                    current != null ? current : rewriteBody(loginUser).cache());
            return cached.map(bytes -> exchange.getResponse().bufferFactory().wrap(bytes)).flux();
        }

        private Mono<byte[]> rewriteBody(Object loginUser) {
            return DataBufferUtils.join(super.getBody())
                    .map(this::drainBuffer)
                    .flatMap(bytes -> {
                        // 二次校验实际读取字节数（应对 chunked 等无 Content-Length 场景）
                        if (bytes.length > MAX_BODY_BUFFER_SIZE) {
                            return Mono.just(bytes);
                        }
                        return Mono.fromCallable(() -> injectLoginUser(bytes, loginUser))
                                .subscribeOn(Schedulers.boundedElastic())
                                .onErrorReturn(bytes);
                    })
                    // 空 Body 降级：构造仅含 loginUser 的 JSON
                    .switchIfEmpty(Mono.fromCallable(() -> {
                                ObjectNode emptyJson = objectMapper.createObjectNode();
                                emptyJson.set(LOGIN_USER, objectMapper.valueToTree(loginUser));
                                return objectMapper.writeValueAsBytes(emptyJson);
                            })
                            .subscribeOn(Schedulers.boundedElastic()));
        }

        private byte[] drainBuffer(DataBuffer dataBuffer) {
            byte[] bytes = new byte[dataBuffer.readableByteCount()];
            dataBuffer.read(bytes);
            DataBufferUtils.release(dataBuffer);
            return bytes;
        }

        private byte[] injectLoginUser(byte[] bytes, Object loginUser) {
            JsonNode jsonNode = objectMapper.readTree(bytes);
            if (jsonNode.isObject()) {
                ((ObjectNode) jsonNode).set(LOGIN_USER, objectMapper.valueToTree(loginUser));
            }
            return objectMapper.writeValueAsBytes(jsonNode);
        }

        @Override
        public HttpHeaders getHeaders() {
            var headers = new HttpHeaders();
            headers.putAll(super.getHeaders());
            if (isJsonContentType(super.getHeaders().getContentType())) {
                // Body 被重写后长度必然变化，必须移除旧 Content-Length
                headers.remove(HttpHeaders.CONTENT_LENGTH);
            }
            return headers;
        }

        private boolean isJsonContentType(@Nullable MediaType contentType) {
            return contentType != null && contentType.isCompatibleWith(MediaType.APPLICATION_JSON);
        }
    }
}