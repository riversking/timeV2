package com.rivers.gateway.filter;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.rivers.core.config.FilterIgnorePropertiesConfig;
import com.rivers.core.entity.LoginUser;
import com.rivers.core.util.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.NullMarked;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.factory.rewrite.ModifyRequestBodyGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.rewrite.RewriteFunction;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.*;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/**
 * 网关统一鉴权 — Cookie 会话模式
 * <p>
 * 职责：
 * 1. 读 Cookie(SESSION_ID) → 查 Redis 会话 → 得双 token
 * 2. access_token 有效 → 续期 TTL → 注入 loginUser 给下游
 * 3. access_token 过期 → 用 refresh_token 换新 → 更新 session → 注入 loginUser
 * <p>
 * session 只存 {accessToken, refreshToken}，用户信息从 JWT claims 还原。
 * LoginUser 加字段不需要改此处任何代码。
 *
 * @author riversking
 */
@Component
@Slf4j
public class RequestGlobalFilter implements GlobalFilter, Ordered {

    private static final String CODE_401 = "{\"code\":401,\"msg\":\"鉴权失败\"}";
    private static final String ATTR_LOGIN_USER = "gateway.loginUser";
    private static final String SESSION_PREFIX = "session:";
    private static final String TOKEN_PREFIX = "token:";
    private static final String REFRESH_PREFIX = "refresh:token:";
    private static final String COOKIE_SESSION = "SESSION_ID";
    private static final String SESSION_KEY_TOKEN = "accessToken";
    private static final String SESSION_KEY_REFRESH = "refreshToken";
    private static final long JWT_EXPIRE_MINUTES = 30L;
    private static final long REFRESH_EXPIRE_DAYS = 30L;

    private final GatewayFilter delegate;
    private final FilterIgnorePropertiesConfig filterIgnorePropertiesConfig;
    private final PathMatcher pathMatcher = new AntPathMatcher();
    private final StringRedisTemplate stringRedisTemplate;

    public RequestGlobalFilter(FilterIgnorePropertiesConfig filterIgnorePropertiesConfig,
                               StringRedisTemplate stringRedisTemplate) {
        this.filterIgnorePropertiesConfig = filterIgnorePropertiesConfig;
        this.stringRedisTemplate = stringRedisTemplate;
        this.delegate = new ModifyRequestBodyGatewayFilterFactory().apply(this.getConfig());
    }

    private ModifyRequestBodyGatewayFilterFactory.Config getConfig() {
        ModifyRequestBodyGatewayFilterFactory.Config cf = new ModifyRequestBodyGatewayFilterFactory.Config();
        cf.setRewriteFunction(Object.class, Object.class, getRewriteFunction());
        return cf;
    }

    @Override
    @NullMarked
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();
        if (filterIgnorePropertiesConfig.getUrls().stream().anyMatch(i -> pathMatcher.match(i, path))) {
            return chain.filter(exchange);
        }
        HttpCookie sessionCookie = request.getCookies().getFirst(COOKIE_SESSION);
        if (sessionCookie == null || StringUtils.isBlank(sessionCookie.getValue())) {
            return respond401(exchange);
        }
        String sessionId = sessionCookie.getValue();
        String sessionJson = stringRedisTemplate.opsForValue().get(SESSION_PREFIX + sessionId);
        if (StringUtils.isBlank(sessionJson)) {
            return clearCookieAnd401(exchange);
        }
        JSONObject session = JSONUtil.parseObj(sessionJson);
        String accessToken = session.getStr(SESSION_KEY_TOKEN);
        String refreshToken = session.getStr(SESSION_KEY_REFRESH);
        LoginUser loginUser = tryAuthenticate(accessToken);
        if (loginUser == null) {
            loginUser = tryRefreshAndUpdateSession(accessToken, refreshToken, sessionId);
        }
        if (loginUser == null) {
            stringRedisTemplate.delete(SESSION_PREFIX + sessionId);
            return clearCookieAnd401(exchange);
        }
        exchange.getAttributes().put(ATTR_LOGIN_USER, loginUser);
        ServerHttpRequest mutatedRequest = request.mutate()
                .header("X-User-Id", loginUser.getUserId())
                .header("X-User-Name", loginUser.getUsername())
                .build();
        ServerWebExchange mutatedExchange = exchange.mutate().request(mutatedRequest).build();
        if (request.getMethod() == HttpMethod.GET) {
            return handleGetRequest(mutatedExchange, chain, loginUser.getUserId());
        }
        return delegate.filter(mutatedExchange, chain);
    }

    /**
     * 校验 access_token：JWT 解析通过 + Redis 中存在且值匹配 → 续期
     */
    private LoginUser tryAuthenticate(String token) {
        if (StringUtils.isBlank(token)) {
            return null;
        }
        Claims claims = JwtUtil.parseJwt(token);
        if (claims == null) {
            return null;
        }
        String jti = claims.getId();
        String redisToken = stringRedisTemplate.opsForValue().get(TOKEN_PREFIX + jti);
        if (StringUtils.isBlank(redisToken) || !Objects.equals(redisToken, token)) {
            return null;
        }
        // 续期
        stringRedisTemplate.expire(TOKEN_PREFIX + jti,
                Duration.ofMinutes(JWT_EXPIRE_MINUTES));
        return extractLoginUser(claims);
    }

    /**
     * access_token 过期 → 用 refresh_token 换新，同时更新 session。
     * LoginUser 从旧 JWT 还原（容忍过期），无需查 session。
     * refreshToken 不轮换。
     */
    private LoginUser tryRefreshAndUpdateSession(String oldAccessToken, String refreshToken, String sessionId) {
        if (StringUtils.isBlank(refreshToken)) {
            return null;
        }
        String userId = stringRedisTemplate.opsForValue()
                .get(REFRESH_PREFIX + refreshToken);
        if (StringUtils.isBlank(userId)) {
            return null;
        }
        Claims claims = JwtUtil.parseJwt(oldAccessToken);
        LoginUser loginUser = extractLoginUser(claims);
        if (loginUser == null) {
            return null;
        }
        String newKey = UUID.randomUUID().toString();
        String newAccessToken = JwtUtil.createJwt(loginUser, newKey);
        stringRedisTemplate.opsForValue()
                .set(TOKEN_PREFIX + newKey, newAccessToken,
                        Duration.ofMinutes(JWT_EXPIRE_MINUTES));
        Map<String, String> newSession = new LinkedHashMap<>();
        newSession.put(SESSION_KEY_TOKEN, newAccessToken);
        newSession.put(SESSION_KEY_REFRESH, refreshToken);
        stringRedisTemplate.opsForValue()
                .set(SESSION_PREFIX + sessionId,
                        JSONUtil.toJsonStr(newSession),
                        Duration.ofDays(REFRESH_EXPIRE_DAYS));
        log.info("Token 自动刷新成功: sessionId={}, userId={}", sessionId, userId);
        return loginUser;
    }
    private LoginUser extractLoginUser(Claims claims) {
        Object user = claims.get("loginUser");
        if (user != null) {
            return BeanUtil.toBean(user, LoginUser.class);
        }
        return null;
    }

    /**
     * 401 + 清除 cookie
     */
    private Mono<Void> clearCookieAnd401(ServerWebExchange exchange) {
        ResponseCookie clearCookie = ResponseCookie.from(COOKIE_SESSION, "")
                .httpOnly(true).path("/").maxAge(0).build();
        exchange.getResponse().addCookie(clearCookie);
        return respond401(exchange);
    }

    private Mono<Void> respond401(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer buffer = response.bufferFactory()
                .wrap(CODE_401.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    /**
     * GET 请求：userId 拼接到 query string
     */
    private Mono<Void> handleGetRequest(ServerWebExchange exchange,
                                        GatewayFilterChain chain,
                                        String userId) {
        ServerHttpRequest request = exchange.getRequest();
        URI uri = request.getURI();
        StringBuilder query = new StringBuilder();
        String originalQuery = uri.getRawQuery();
        if (org.springframework.util.StringUtils.hasText(originalQuery)) {
            query.append(originalQuery);
            if (originalQuery.charAt(originalQuery.length() - 1) != '&') {
                query.append('&');
            }
        }
        query.append("userId=").append(userId);
        URI newUri = UriComponentsBuilder.fromUri(uri)
                .replaceQuery(query.toString())
                .build(true)
                .toUri();
        ServerHttpRequest mutated = request.mutate().uri(newUri).build();
        return chain.filter(exchange.mutate().request(mutated).build());
    }

    private RewriteFunction<Object, Object> getRewriteFunction() {
        return (serverWebExchange, body) -> {
            LoginUser loginUser = serverWebExchange.getAttribute(ATTR_LOGIN_USER);
            LinkedHashMap<String, Object> map = body instanceof LinkedHashMap<?, ?> bodyMap
                    ? (LinkedHashMap<String, Object>) bodyMap
                    : new LinkedHashMap<>();
            Optional.ofNullable(loginUser)
                    .ifPresent(i -> {
                        map.put("loginUser", i);
                        log.info("登录用户信息: {}", loginUser);
                    });
            log.info("request body {}", map);
            return Mono.just(map).map(Object.class::cast);
        };
    }

    @Override
    public int getOrder() {
        return -1000;
    }
}