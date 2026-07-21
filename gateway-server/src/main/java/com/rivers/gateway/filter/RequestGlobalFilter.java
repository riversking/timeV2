package com.rivers.gateway.filter;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import com.rivers.core.config.FilterIgnorePropertiesConfig;
import com.rivers.core.entity.LoginUser;
import com.rivers.core.util.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.NullMarked;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.factory.rewrite.ModifyRequestBodyGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.rewrite.RewriteFunction;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 网关统一鉴权 — Cookie 会话模式（两层 Redis 判定）
 * <p>
 * token:{jti}  TTL = 30min  →  活着就续 TTL，放行
 * session:{sid} TTL = 30d   →  token 死了就查你，活着就继承 TTL 生新 JWT
 * →  session 也死了就 401
 *
 * @author riversking
 */
@Component
@Slf4j
public class RequestGlobalFilter implements GlobalFilter, Ordered {

    private static final String CODE_401 = "{\"code\":401,\"msg\":\"鉴权失败\"}";
    private static final String SESSION_PREFIX = "session:";
    private static final String TOKEN_PREFIX = "token:";
    private static final String COOKIE_SESSION = "SESSION_ID";

    private static final long TOKEN_TTL_MINUTES = 30;

    private static final String ATTR_LOGIN_USER = "gateway.loginUser";

    private final GatewayFilter delegate;
    private final FilterIgnorePropertiesConfig filterIgnorePropertiesConfig;
    private final PathMatcher pathMatcher = new AntPathMatcher();
    private final StringRedisTemplate redisTemplate;

    public RequestGlobalFilter(FilterIgnorePropertiesConfig filterIgnorePropertiesConfig,
                               StringRedisTemplate redisTemplate) {
        this.filterIgnorePropertiesConfig = filterIgnorePropertiesConfig;
        this.redisTemplate = redisTemplate;
        this.delegate = new ModifyRequestBodyGatewayFilterFactory().apply(this.getConfig());
    }

    private ModifyRequestBodyGatewayFilterFactory.Config getConfig() {
        var cf = new ModifyRequestBodyGatewayFilterFactory.Config();
        cf.setRewriteFunction(Object.class, Object.class, getRewriteFunction());
        return cf;
    }

    @Override
    @NullMarked
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        var request = exchange.getRequest();
        var path = request.getPath().value();
        if (filterIgnorePropertiesConfig.getUrls().stream()
                .anyMatch(i -> pathMatcher.match(i, path))) {
            return chain.filter(exchange);
        }
        var cookie = request.getCookies().getFirst(COOKIE_SESSION);
        if (cookie == null || StringUtils.isBlank(cookie.getValue())) {
            return clearSessionAnd401(exchange, null);
        }
        var loginUser = resolveLoginUser(cookie.getValue());
        if (loginUser == null) {
            return clearSessionAnd401(exchange, cookie.getValue());
        }
        // 注入用户身份
        exchange.getAttributes().put(ATTR_LOGIN_USER, loginUser);
        var mutatedRequest = request.mutate()
                .header("X-User-Id", loginUser.getUserId())
                .header("X-User-Name", loginUser.getUsername())
                .build();
        var mutatedExchange = exchange.mutate().request(mutatedRequest).build();
        if (request.getMethod() == HttpMethod.GET) {
            return handleGetRequest(mutatedExchange, chain, loginUser.getUserId());
        }
        return delegate.filter(mutatedExchange, chain);
    }

    // ═══════════════════════════════════════════════════════════════
    //  Session → JWT 解析 + 续签（提炼的核心方法）
    // ═══════════════════════════════════════════════════════════════

    /**
     * 从 session 中解析 JWT，校验并续签，返回 LoginUser。
     * 任一步失败返回 null，调用方直接 401。
     */
    private LoginUser resolveLoginUser(String sessionId) {
        // ① 读 session JSON
        var sessionJson = redisTemplate.opsForValue().get(SESSION_PREFIX + sessionId);
        if (StringUtils.isBlank(sessionJson)) {
            return null;
        }
        var oldToken = JSONUtil.parseObj(sessionJson).getStr("accessToken");
        if (StringUtils.isBlank(oldToken)) {
            return null;
        }
        // ② 解析 JWT（容忍过期）
        var claims = JwtUtil.parseJwt(oldToken);
        if (claims == null) {
            return null;
        }
        var loginUser = extractLoginUser(claims);
        if (loginUser == null) {
            return null;
        }
        // ③ 校验 token 获取key
        var oldKey = claims.getId();
        var redisToken = redisTemplate.opsForValue().get(TOKEN_PREFIX + oldKey);
        if (StringUtils.isBlank(redisToken) || !Objects.equals(oldToken, redisToken)) {
            // ── token 已过期 → 生新 JWT + 更新 session ──
            var newKey = UUID.randomUUID().toString();
            var newToken = JwtUtil.createJwt(loginUser, newKey);
            redisTemplate.opsForValue()
                    .set(TOKEN_PREFIX + newKey, newToken,
                            Duration.ofMinutes(TOKEN_TTL_MINUTES));
            // ★ 只在需要时查 session TTL（省一次 getExpire 调用）
            var ttl = redisTemplate.getExpire(SESSION_PREFIX + sessionId, TimeUnit.SECONDS);
            if (ttl != null && ttl > 0) {
                redisTemplate.opsForValue()
                        .set(SESSION_PREFIX + sessionId,
                                JSONUtil.toJsonStr(Map.of("accessToken", newToken)),
                                Duration.ofSeconds(ttl));
            }
            log.info("JWT 续签: sessionId={}, userId={}",
                    sessionId, loginUser.getUserId());
        } else {
            // ── token 还活着 → 续 TTL ──
            redisTemplate.expire(TOKEN_PREFIX + oldKey,
                    Duration.ofMinutes(TOKEN_TTL_MINUTES));
        }
        return loginUser;
    }

    // ═══════════════════════════════════════════════════════════════
    //  辅助
    // ═══════════════════════════════════════════════════════════════

    private LoginUser extractLoginUser(Claims claims) {
        var user = claims.get("loginUser");
        return user != null ? BeanUtil.toBean(user, LoginUser.class) : null;
    }

    private Mono<Void> clearSessionAnd401(ServerWebExchange exchange, String sessionId) {
        if (StringUtils.isNotBlank(sessionId)) {
            redisTemplate.delete(SESSION_PREFIX + sessionId);
        }
        exchange.getResponse().addCookie(
                ResponseCookie.from(COOKIE_SESSION, "")
                        .httpOnly(true).path("/").maxAge(0).build());
        var response = exchange.getResponse();
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        var buffer = response.bufferFactory()
                .wrap(CODE_401.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    private Mono<Void> handleGetRequest(ServerWebExchange exchange,
                                        GatewayFilterChain chain, String userId) {
        var request = exchange.getRequest();
        var uri = request.getURI();
        var query = new StringBuilder();
        var originalQuery = uri.getRawQuery();
        if (org.springframework.util.StringUtils.hasText(originalQuery)) {
            query.append(originalQuery);
            if (originalQuery.charAt(originalQuery.length() - 1) != '&') {
                query.append('&');
            }
        }
        query.append("userId=").append(userId);
        var newUri = UriComponentsBuilder.fromUri(uri)
                .replaceQuery(query.toString()).build(true).toUri();
        return chain.filter(exchange.mutate()
                .request(request.mutate().uri(newUri).build()).build());
    }

    @SuppressWarnings("unchecked")
    private RewriteFunction<Object, Object> getRewriteFunction() {
        return (exchange, body) -> {
            var loginUser = exchange.getAttribute(ATTR_LOGIN_USER);
            Map<String, Object> map = body instanceof LinkedHashMap<?, ?> bm
                    ? (LinkedHashMap<String, Object>) bm
                    : new LinkedHashMap<>();
            Optional.ofNullable(loginUser)
                    .ifPresent(u -> map.put("loginUser", u));
            return Mono.just(map).map(Object.class::cast);
        };
    }

    @Override
    public int getOrder() {
        return -1000;
    }
}