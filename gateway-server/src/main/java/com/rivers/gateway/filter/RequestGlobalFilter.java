package com.rivers.gateway.filter;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.text.CharSequenceUtil;
import com.alibaba.nacos.shaded.com.google.common.collect.Maps;
import com.rivers.core.config.FilterIgnorePropertiesConfig;
import com.rivers.core.util.JwtUtil;
import com.rivers.proto.LoginUser;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.factory.rewrite.ModifyRequestBodyGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.rewrite.RewriteFunction;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Optional;

@Component
@Slf4j
public class RequestGlobalFilter implements GlobalFilter, Ordered {

    private final GatewayFilter delegate;

    private final FilterIgnorePropertiesConfig filterIgnorePropertiesConfig;

    private PathMatcher pathMatcher = new AntPathMatcher();

    public RequestGlobalFilter(FilterIgnorePropertiesConfig filterIgnorePropertiesConfig) {
        this.filterIgnorePropertiesConfig = filterIgnorePropertiesConfig;
        this.delegate = new ModifyRequestBodyGatewayFilterFactory().apply(this.getConfig());
    }

    private ModifyRequestBodyGatewayFilterFactory.Config getConfig() {
        ModifyRequestBodyGatewayFilterFactory.Config cf = new ModifyRequestBodyGatewayFilterFactory.Config();
        cf.setRewriteFunction(Object.class, Object.class, getRewriteFunction());
        return cf;
    }

    @Override
    @NonNull
    public Mono<Void> filter(ServerWebExchange exchange, @NonNull GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();
        if (filterIgnorePropertiesConfig.getUrls().stream().anyMatch(i -> pathMatcher.match(i, path))) {
            return chain.filter(exchange);
        }
        if (request.getMethod() == HttpMethod.GET) {
            URI uri = request.getURI();
            StringBuilder query = new StringBuilder();
            String originalQuery = uri.getRawQuery();
            if (org.springframework.util.StringUtils.hasText(originalQuery)) {
                query.append(originalQuery);
                if (originalQuery.charAt(originalQuery.length() - 1) != '&') {
                    query.append('&');
                }
            }
            // 添加查询参数
            query.append("&" + "userId" + "=" + "a");
            // 替换查询参数
            URI newUri = UriComponentsBuilder.fromUri(uri)
                    .replaceQuery(query.toString())
                    .build(true)
                    .toUri();
            ServerHttpRequest serverHttpRequest = request.mutate().uri(newUri).build();
            return chain.filter(exchange.mutate().request(serverHttpRequest).build());
        }
        // post 请求 特殊处理
        return delegate.filter(exchange, chain);
    }

    @Override
    public int getOrder() {
        return 0;
    }


    private RewriteFunction<Object, Object> getRewriteFunction() {
        return (serverWebExchange, body) -> {
            // 这里的body就是请求体参数, 类型是LinkedHashMap, 可以根据需要转成JSON
            ServerHttpRequest request = serverWebExchange.getRequest();
            HttpHeaders headers = request.getHeaders();
            String authorization = headers.getFirst(HttpHeaders.AUTHORIZATION);
            String token = CharSequenceUtil.subAfter(authorization, "Bearer ", false);
            Claims claims = JwtUtil.parseJwt(token);
            LinkedHashMap<String, Object> map = body instanceof LinkedHashMap<?, ?> bodyMap
                    ? (LinkedHashMap<String, Object>) bodyMap : Maps.newLinkedHashMap();
            Optional.ofNullable(claims)
                    .ifPresent(i -> {
                        Object user = i.get("loginUser");
                        LoginUser loginUser = BeanUtil.toBean(user, LoginUser.class);
                        map.put("loginUser", loginUser);
                        log.info("登录用户信息: {}", loginUser);
                    });
            return Mono.just(map)
                    .map(Object.class::cast)
                    .doFinally(i -> map.clear());
        };
    }
}
