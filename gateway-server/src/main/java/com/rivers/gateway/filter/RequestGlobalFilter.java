package com.rivers.gateway.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 网关统一鉴权 — Cookie 会话模式 + WebFilter 拦截（含网关本地 /logout）
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
    //  主链
    // ═══════════════════════════════════════════════════════════════

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        var request = exchange.getRequest();
        var path = request.getPath().value();
        // 1. 白名单直接放行
        if (filterIgnorePropertiesConfig.getUrls().stream().anyMatch(i -> pathMatcher.match(i, path))) {
            return chain.filter(exchange);
        }
        var cookie = request.getCookies().getFirst(COOKIE_SESSION);
        // 2. 无 Cookie → 401
        if (cookie == null || !StringUtils.hasText(cookie.getValue())) {
            return clearSessionAnd401(exchange, null);
        }
        var sessionId = cookie.getValue();
        // 3. 主链：读 session → 解析+校验 → 续/刷新 → 注入身份 → 转发（两层 flatMap + switchIfEmpty 兜底）
        return redisTemplate.opsForValue().get(SESSION_PREFIX + sessionId)
                .filter(StringUtils::hasText)
                .flatMap(sessionJson -> resolveLoginUser(sessionJson)
                        .flatMap(ctx -> refreshOrRenew(sessionId, ctx)))
                .flatMap(loginUser -> {
                    exchange.getAttributes().put(ATTR_LOGIN_USER, loginUser);
                    var mutatedRequest = request.mutate()
                            .header("X-User-Id", loginUser.getUserId())
                            .header("X-User-Name", loginUser.getUsername())
                            .build();
                    var finalRequest = request.getMethod() == HttpMethod.GET
                            ? handleGetRequest(mutatedRequest, loginUser.getUserId())
                            : new BodyRewriteDecorator(mutatedRequest, exchange);
                    return chain.filter(exchange.mutate().request(finalRequest).build());
                })
                .switchIfEmpty(clearSessionAnd401(exchange, sessionId));
    }

    // ═══════════════════════════════════════════════════════════════
    //  Session JSON → 取 token → 解析 JWT → TokenContext，一次 offload
    // ═══════════════════════════════════════════════════════════════

    private Mono<TokenContext> resolveLoginUser(String sessionJson) {
        return Mono.fromCallable(() -> {
                    var oldToken = objectMapper.readTree(sessionJson)
                            .path("accessToken").asText(null);
                    if (!StringUtils.hasText(oldToken)) {
                        return null;
                    }
                    var claims = JwtUtil.parseJwt(oldToken);
                    if (claims == null) {
                        return null;
                    }
                    var loginUser = extractLoginUser(claims);
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
                    return renewTtlThenReturn(ctx);
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

    /**
     * Token 有效 → 仅续 TTL
     */
    private Mono<LoginUser> renewTtlThenReturn(TokenContext ctx) {
        return redisTemplate.expire(TOKEN_PREFIX + ctx.oldKey, Duration.ofMinutes(TOKEN_TTL_MINUTES))
                .thenReturn(ctx.loginUser);
    }

    // ═══════════════════════════════════════════════════════════════
    //  401 + 清除 Session Cookie
    // ═══════════════════════════════════════════════════════════════

    private Mono<Void> clearSessionAnd401(ServerWebExchange exchange, @Nullable String sessionId) {
        var cleanup = StringUtils.hasText(sessionId)
                ? redisTemplate.delete(SESSION_PREFIX + sessionId)
                : Mono.empty();

        exchange.getResponse().addCookie(
                ResponseCookie.from(COOKIE_SESSION, "").httpOnly(true).path("/").maxAge(0).build());
        var response = exchange.getResponse();
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        var buffer = response.bufferFactory().wrap(CODE_401.getBytes(StandardCharsets.UTF_8));

        return cleanup.then(response.writeWith(Mono.just(buffer)));
    }

    // ═══════════════════════════════════════════════════════════════
    //  GET 请求：userId 拼入 Query String
    // ═══════════════════════════════════════════════════════════════

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
    //  内部类：Body 重写装饰器（OOM 防护 + 空 Body 降级 + 非 JSON 降级）
    // ═══════════════════════════════════════════════════════════════

    private class BodyRewriteDecorator extends ServerHttpRequestDecorator {

        private final ServerWebExchange exchange;
        private static final int MAX_BODY_BUFFER_SIZE = 1024 * 1024;

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
            return DataBufferUtils.join(super.getBody())
                    .filter(dataBuffer -> dataBuffer.readableByteCount() <= MAX_BODY_BUFFER_SIZE)
                    .flatMap(dataBuffer -> {
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
            headers.remove(HttpHeaders.CONTENT_LENGTH);
            return headers;
        }
    }
}