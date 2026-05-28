package com.rivers.im.config;

import com.rivers.im.service.impl.AuthHandshakeWebSocketService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;

import java.util.Map;

@Configuration
@RequiredArgsConstructor
public class WebSocketConfig {

    // 只依赖 Handler，不再产生循环
    private final UnifiedWebSocketHandler unifiedHandler;

    private final AuthHandshakeWebSocketService authHandshakeService;

    @Bean
    public HandlerMapping webSocketMapping() {
        SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();
        mapping.setUrlMap(Map.of("/ws", unifiedHandler));
        mapping.setOrder(-1); // 保证优先级最高
        return mapping;
    }

    @Bean
    public WebSocketHandlerAdapter handlerAdapter() {
        // 注入自定义的鉴权握手服务
        return new WebSocketHandlerAdapter(authHandshakeService);
    }
}