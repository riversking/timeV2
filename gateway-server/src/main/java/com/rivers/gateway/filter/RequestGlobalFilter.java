package com.rivers.gateway.filter;

import com.rivers.core.config.FilterIgnorePropertiesConfig;
import com.rivers.core.entity.LoginUser;
import com.rivers.core.util.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
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

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 网关统一鉴权 — Cookie 会话模式 + WebFilter 拦截
 * <p>
 * token:{jti}  TTL = 30min  →  活着就续 TTL，放行
 * session:{sid} TTL = 30d   →  token 死了就查你，活着就旋转新 JWT
 * →  双死就 401
 *
 * @author riversking
 */
@Component
@Slf4j
@NullMarked
public class RequestGlobalFilter implements WebFilter, Ordered {

    private static final String CODE_401 = "{\"code\":401,\"msg\":\"鉴权失败\"}";
    private static final String SESSION_PREFIX = "session:";
    private static final String TOKEN_PREFIX = "token:";
    private static final String COOKIE_SESSION = "SESSION_ID";
    private static final long TOKEN_TTL_MINUTES = 30;
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
    //  主入口：同步判断用 if-else，异步流程用语义化方法
    // ═══════════════════════════════════════════════════════════════
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        var request = exchange.getRequest();
        var path = request.getPath().value();
        // ✅ 同步判断：白名单直接放行
        if (filterIgnorePropertiesConfig.getUrls().stream()
                .anyMatch(pattern -> pathMatcher.match(pattern, path))) {
            return chain.filter(exchange);
        }
        // ✅ 同步判断：无 Cookie → 401
        var sessionId = extractSessionId(request);
        if (sessionId == null) {
            return clearSessionAnd401(exchange, null);
        }
        // ✅ 异步主流程：读起来像线性伪代码
        return loadValidUser(sessionId)
                .flatMap(user -> forwardWithIdentity(exchange, chain, user))
                .switchIfEmpty(clearSessionAnd401(exchange, sessionId));
    }

    private @Nullable String extractSessionId(ServerHttpRequest request) {
        var cookie = request.getCookies().getFirst(COOKIE_SESSION);
        return (cookie != null && StringUtils.hasText(cookie.getValue()))
                ? cookie.getValue() : null;
    }

    // ═══════════════════════════════════════════════════════════════
    //  异步语义层1: 加载Session → 解析JWT → 校验/刷新Token → 返回有效用户
    //  任何环节失败都返回 Mono.empty()，由调用方 switchIfEmpty 统一处理
    // ═══════════════════════════════════════════════════════════════
    private Mono<LoginUser> loadValidUser(String sessionId) {
        return redisTemplate.opsForValue().get(SESSION_PREFIX + sessionId)
                .filter(StringUtils::hasText)
                .flatMap(json -> resolveLoginUser(json)
                        .flatMap(ctx -> refreshOrRenew(sessionId, ctx)));
    }

    /**
     * Session JSON → 取 token → 解析 JWT → TokenContext
     * CPU 密集操作 offload 到 boundedElastic
     */
    private Mono<TokenContext> resolveLoginUser(String sessionJson) {
        return Mono.fromCallable(() -> {
                    var oldToken = objectMapper.readTree(sessionJson)
                            .path("accessToken").asString(null);
                    if (!StringUtils.hasText(oldToken)) {
                        return null;
                    }
                    var claims = JwtUtil.parseJwt(oldToken);
                    if (claims == null) {
                        return null;
                    }
                    var loginUser = extractLoginUser(claims);
                    if (loginUser == null) {
                        return null;
                    }
                    return new TokenContext(oldToken, claims.getId(), loginUser);
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * token:{key} 在 Redis 中的值是否匹配 → 决定刷新还是续期
     */
    private Mono<LoginUser> refreshOrRenew(String sessionId, TokenContext ctx) {
        return redisTemplate.opsForValue().get(TOKEN_PREFIX + ctx.oldKey)
                .flatMap(redisToken -> {
                    if (!StringUtils.hasText(redisToken) || !Objects.equals(ctx.oldToken, redisToken)) {
                        return rotateToken(sessionId, ctx);
                    }
                    return redisTemplate.expire(TOKEN_PREFIX + ctx.oldKey, Duration.ofMinutes(TOKEN_TTL_MINUTES))
                            .thenReturn(ctx.loginUser);
                });
    }

    /**
     * Token 过期/不匹配 → 旋转：生新 JWT + 新 token key + 更新 session
     */
    private Mono<LoginUser> rotateToken(String sessionId, TokenContext ctx) {
        var newKey = UUID.randomUUID().toString();
        var newToken = JwtUtil.createJwt(ctx.loginUser, newKey);
        return redisTemplate.getExpire(SESSION_PREFIX + sessionId)
                .filter(ttl -> !ttl.isZero() && !ttl.isNegative())
                .flatMap(ttl -> Mono.fromCallable(() ->
                                objectMapper.writeValueAsString(Map.of("accessToken", newToken)))
                        .subscribeOn(Schedulers.boundedElastic())
                        .flatMap(newSessionJson ->
                                redisTemplate.opsForValue()
                                        .set(TOKEN_PREFIX + newKey, newToken, Duration.ofMinutes(TOKEN_TTL_MINUTES))
                                        .then(redisTemplate.opsForValue()
                                                .set(SESSION_PREFIX + sessionId, newSessionJson, ttl))
                                        .thenReturn(ctx.loginUser)))
                .switchIfEmpty(Mono.just(ctx.loginUser));
    }

    // ═══════════════════════════════════════════════════════════════
    //  异步语义层2: 注入身份 + 构建请求 + 转发
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
     * GET 请求：userId 拼入 Query String
     */
    private ServerHttpRequest handleGetRequest(ServerHttpRequest request, String userId) {
        var uri = request.getURI();
        var query = new StringBuilder();
        var originalQuery = uri.getRawQuery();
        if (StringUtils.hasText(originalQuery)) {
            query.append(originalQuery);
            if (originalQuery.charAt(originalQuery.length() - 1) != '&') {
                query.append('&');
            }
        }
        query.append("userId=").append(userId);
        var newUri = UriComponentsBuilder.fromUri(uri)
                .replaceQuery(query.toString()).build(true).toUri();
        return request.mutate().uri(newUri).build();
    }

    // ═══════════════════════════════════════════════════════════════
    //  401 + 清除 Session Cookie
    // ═══════════════════════════════════════════════════════════════
    private Mono<Void> clearSessionAnd401(ServerWebExchange exchange, @Nullable String sessionId) {
        var cleanup = StringUtils.hasText(sessionId)
                ? redisTemplate.delete(SESSION_PREFIX + sessionId)
                : Mono.empty();
        exchange.getResponse().addCookie(
                ResponseCookie.from(COOKIE_SESSION, "")
                        .httpOnly(true).path("/")
                        .maxAge(0)
                        .build());
        var response = exchange.getResponse();
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        var buffer = response.bufferFactory().wrap(CODE_401.getBytes(StandardCharsets.UTF_8));
        return cleanup.then(response.writeWith(Mono.just(buffer)));
    }

    // ═══════════════════════════════════════════════════════════════
    //  辅助
    // ═══════════════════════════════════════════════════════════════
    private @Nullable LoginUser extractLoginUser(Claims claims) {
        var user = claims.get(LOGIN_USER);
        return user != null ? objectMapper.convertValue(user, LoginUser.class) : null;
    }

    @Override
    public int getOrder() {
        return -1000;
    }

    /**
     * 解析阶段的中间态上下文
     */
    private record TokenContext(String oldToken, String oldKey, LoginUser loginUser) {
    }

    // ═══════════════════════════════════════════════════════════════
    //  Body 重写装饰器
    //  ✅ 仅处理 JSON + 预检 Content-Length + OOM 防护 + 空 Body 降级
    // ═══════════════════════════════════════════════════════════════
    private class BodyRewriteDecorator extends ServerHttpRequestDecorator {

        private final ServerWebExchange exchange;

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
            // ✅ 预检：非 JSON 直接透传，不做任何缓冲
            var contentType = getHeaders().getContentType();
            if (contentType == null || !contentType.isCompatibleWith(MediaType.APPLICATION_JSON)) {
                return super.getBody();
            }
            // ✅ 预检：Content-Length 超过阈值直接透传，防止 OOM
            long contentLength = getHeaders().getContentLength();
            if (contentLength > MAX_BODY_BUFFER_SIZE) {
                log.warn("Body size {} exceeds limit {}, skip loginUser injection for {}",
                        contentLength, MAX_BODY_BUFFER_SIZE, getURI().getPath());
                return super.getBody();
            }
            return DataBufferUtils.join(super.getBody())
                    .flatMap(dataBuffer -> {
                        // 二次校验实际读取字节数（应对 chunked 等无 Content-Length 场景）
                        if (dataBuffer.readableByteCount() > MAX_BODY_BUFFER_SIZE) {
                            return Mono.just(dataBuffer);
                        }
                        byte[] bytes = new byte[dataBuffer.readableByteCount()];
                        dataBuffer.read(bytes);
                        DataBufferUtils.release(dataBuffer);
                        return Mono.fromCallable(() -> {
                                    JsonNode jsonNode = objectMapper.readTree(bytes);
                                    if (jsonNode.isObject()) {
                                        ((ObjectNode) jsonNode).set(LOGIN_USER,
                                                objectMapper.valueToTree(loginUser));
                                    }
                                    return objectMapper.writeValueAsBytes(jsonNode);
                                })
                                .subscribeOn(Schedulers.boundedElastic())
                                .onErrorReturn(bytes)
                                .map(exchange.getResponse().bufferFactory()::wrap);
                    })
                    // 空 Body 降级：构造仅含 loginUser 的 JSON
                    .switchIfEmpty(Mono.fromCallable(() -> {
                                ObjectNode emptyJson = objectMapper.createObjectNode();
                                emptyJson.set(LOGIN_USER, objectMapper.valueToTree(loginUser));
                                return objectMapper.writeValueAsBytes(emptyJson);
                            })
                            .subscribeOn(Schedulers.boundedElastic())
                            .map(exchange.getResponse().bufferFactory()::wrap))
                    .flux();
        }

        @Override
        public HttpHeaders getHeaders() {
            var headers = new HttpHeaders();
            headers.putAll(super.getHeaders());
            // Body 被重写后长度必然变化，必须移除旧 Content-Length
            headers.remove(HttpHeaders.CONTENT_LENGTH);
            return headers;
        }
    }
}