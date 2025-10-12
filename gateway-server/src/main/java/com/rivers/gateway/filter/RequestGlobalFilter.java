package com.rivers.gateway.filter;

import cn.hutool.core.text.CharSequenceUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.nacos.shaded.com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.factory.rewrite.ModifyRequestBodyGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.rewrite.RewriteFunction;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.RequestPath;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
@Slf4j
public class RequestGlobalFilter implements GlobalFilter, Ordered {

    private final GatewayFilter delegate;

    public RequestGlobalFilter() {
        this.delegate =  new ModifyRequestBodyGatewayFilterFactory().apply(this.getConfig());
    }

    private ModifyRequestBodyGatewayFilterFactory.Config getConfig() {
        ModifyRequestBodyGatewayFilterFactory.Config cf = new ModifyRequestBodyGatewayFilterFactory.Config();
        cf.setRewriteFunction(Object.class, Object.class, getRewriteFunction());
        return cf;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (exchange.getRequest().getMethod() == HttpMethod.GET) {
            URI uri = exchange.getRequest().getURI();
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
            ServerHttpRequest request = exchange.getRequest().mutate().uri(newUri).build();
            return chain.filter(exchange.mutate().request(request).build());
        }
        RequestPath path = exchange.getRequest().getPath();
        if (path.toString().contains("upload")) {
            return chain.filter(exchange);
        }
        //post 请求 特殊处理
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
            if (request.getPath().toString().contains("login")) {
                return Mono.just(body);
            }
            Map<String, Object> user = Maps.newLinkedHashMap();
            List<String> list = headers.get("Authorization");
            String authorization = Objects.requireNonNull(list).stream().findFirst().orElse(null);
            String token = CharSequenceUtil.subAfter(authorization, "Bearer ", false);
            String claims = JwtHelper.decode(token).getClaims();
            JSONObject json = JSON.parseObject(claims);
            LinkedHashMap<String, Object> map = (LinkedHashMap<String, Object>) body;
            if (MapUtils.isEmpty(map)) {
                map = Maps.newLinkedHashMap();
            }
            user.put("id", json.get("id"));
            user.put("userId", json.get("userId"));
            map.put("user", user);
            log.info("请求体: {}", JSON.toJSONString(map));
            return Mono.just(map);
        };
    }
}
