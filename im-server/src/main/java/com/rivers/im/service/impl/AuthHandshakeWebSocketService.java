package com.rivers.im.service.impl;

import com.rivers.im.service.IWsTicketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.server.support.HandshakeWebSocketService;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@Slf4j
@RequiredArgsConstructor
public class AuthHandshakeWebSocketService extends HandshakeWebSocketService {

    private final IWsTicketService wsTicketService;

    @Override
    @NullMarked
    public Mono<Void> handleRequest(ServerWebExchange exchange, WebSocketHandler handler) {
        String query = exchange.getRequest().getURI().getQuery();
        String ticket = extractParam(query, "ticket");

        if (ticket == null || ticket.isBlank()) {
            log.warn("❌ WS握手拒绝: 缺少 ticket");
            return Mono.error(new RuntimeException("Missing ticket"));
        }
        return wsTicketService.consumeTicket(ticket)
                .flatMap(userId -> {
                    // 🌟 核心：将 userId 焊死在 Session Attributes 上
                    exchange.getAttributes().put("userId", userId);
                    log.info("✅ WS握手成功: userId={}", userId);
                    return super.handleRequest(exchange, handler);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("❌ WS握手拒绝: ticket 无效或已过期");
                    return Mono.error(new RuntimeException("Invalid ticket"));
                }));
    }

    private String extractParam(String query, String key) {
        if (query == null) {
            return null;
        }
        for (String param : query.split("&")) {
            String[] kv = param.split("=", 2);
            if (kv.length == 2 && kv[0].equals(key)) {
                return java.net.URLDecoder.decode(kv[1], java.nio.charset.StandardCharsets.UTF_8);
            }
        }
        return null;
    }
}