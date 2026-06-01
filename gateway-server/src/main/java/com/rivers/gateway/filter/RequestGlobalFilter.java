package com.rivers.gateway.filter;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.text.CharSequenceUtil;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
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
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * @author riversking
 */
@Component
@Slf4j
public class RequestGlobalFilter implements GlobalFilter, Ordered {

    private static final String CODE_401 = "{\"code\":401,\"msg\":\"鉴权失败\"}";
    private static final String ATTR_CLAIMS = "gateway.claims";
    private static final String ATTR_LOGIN_USER = "gateway.loginUser";

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
        HttpHeaders headers = request.getHeaders();
        String authorization = headers.getFirst(HttpHeaders.AUTHORIZATION);
        if (StringUtils.isBlank(authorization)) {
            return respond401(exchange);
        }
        String token = CharSequenceUtil.subAfter(authorization, "Bearer ", false);
        Claims claims = JwtUtil.parseJwt(token);
        if (claims == null) {
            return respond401(exchange);
        }
        String claimsId = claims.getId();
        String redisToken = stringRedisTemplate.opsForValue().get("token:" + claimsId);
        if (StringUtils.isBlank(redisToken)) {
            return respond401(exchange);
        }
        if (!Objects.equals(redisToken, token)) {
            return respond401(exchange);
        }
        stringRedisTemplate.expire("token:" + claimsId, 30, TimeUnit.MINUTES);
        LoginUser loginUser = extractLoginUser(claims);
        if (loginUser == null) {
            return respond401(exchange);
        }
        exchange.getAttributes().put(ATTR_LOGIN_USER, loginUser);
        if (request.getMethod() == HttpMethod.GET) {
            return handleGetRequest(exchange, chain, loginUser.getUserId());
        }
        return delegate.filter(exchange, chain);
    }

    private LoginUser extractLoginUser(Claims claims) {
        Object user = claims.get("loginUser");
        if (user != null) {
            return BeanUtil.toBean(user, LoginUser.class);
        }
        return null;
    }

    private Mono<Void> respond401(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        DataBuffer buffer = response.bufferFactory().wrap(CODE_401.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    private Mono<Void> handleGetRequest(ServerWebExchange exchange, GatewayFilterChain chain, String userId) {
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
        ServerHttpRequest serverHttpRequest = request.mutate().uri(newUri).build();
        return chain.filter(exchange.mutate().request(serverHttpRequest).build());
    }

    @Override
    public int getOrder() {
        return -1000;
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
}
