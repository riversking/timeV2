package com.rivers.im.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.server.WebSocketService;
import org.springframework.web.reactive.socket.server.support.HandshakeWebSocketService;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;
import org.springframework.web.reactive.socket.server.upgrade.ReactorNettyRequestUpgradeStrategy;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.HashMap;
import java.util.Map;

@Configuration
@Slf4j
public class WebSocketConfig {

    private final ChatWebSocketHandler chatWebSocketHandler;

    public WebSocketConfig(ChatWebSocketHandler chatWebSocketHandler) {
        this.chatWebSocketHandler = chatWebSocketHandler;
    }

    /**
     * WebSocket 路由映射（直接在 Handler 中处理认证）
     */
    @Bean
    public HandlerMapping webSocketHandlerMapping() {
        Map<String, WebSocketHandler> handlerMap = new HashMap<>();

        // 包装 Handler：在握手阶段进行认证
        WebSocketHandler wrappedHandler = session -> {
            // 1️⃣ 握手认证逻辑
            Long userId = extractUserIdFromSession(session);
            if (userId == null) {
                log.warn("❌ Handshake rejected: userId not found");
                return session.close();
            }

            // 2️⃣ 将 userId 存入 session attributes
            session.getAttributes().put("userId", userId);
            log.info("✅ Handshake success | userId: {} | session: {}", userId, session.getId());

            // 3️⃣ 交给实际 Handler 处理
            return chatWebSocketHandler.handle(session);
        };

        handlerMap.put("/ws/chat", wrappedHandler);

        SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();
        mapping.setUrlMap(handlerMap);
        mapping.setOrder(1);
        return mapping;
    }

    /**
     * WebSocket 适配器
     */
    @Bean
    public WebSocketHandlerAdapter webSocketHandlerAdapter() {
        return new WebSocketHandlerAdapter(webSocketService());
    }

    /**
     * WebSocket 服务
     */
    @Bean
    public WebSocketService webSocketService() {
        return new HandshakeWebSocketService(upgradeStrategy());
    }

    /**
     * 升级策略（Reactor Netty）
     */
    @Bean
    public ReactorNettyRequestUpgradeStrategy upgradeStrategy() {
        return new ReactorNettyRequestUpgradeStrategy();
    }

    /**
     * CORS 配置
     */
    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.addAllowedOrigin("*");
        config.addAllowedHeader("*");
        config.addAllowedMethod("*");
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/ws/**", config);

        return new CorsWebFilter(source);
    }

    /**
     * 从 WebSocketSession 提取 userId
     */
    private Long extractUserIdFromSession(org.springframework.web.reactive.socket.WebSocketSession session) {
        try {
            // 从 URI 查询参数提取 userId（示例：/ws/chat?userId=123）
            String query = session.getHandshakeInfo().getUri().getQuery();
            if (query == null) {
                return null;
            }

            String userIdParam = extractQueryParam(query, "userId");
            if (userIdParam == null) {
                return null;
            }

            return Long.parseLong(userIdParam);
        } catch (Exception e) {
            log.warn("❌ Failed to extract userId: {}", e.getMessage());
            return null;
        }
    }

    private String extractQueryParam(String query, String paramName) {
        String[] params = query.split("&");
        for (String param : params) {
            String[] pair = param.split("=");
            if (pair.length == 2 && pair[0].equals(paramName)) {
                return pair[1];
            }
        }
        return null;
    }
}
