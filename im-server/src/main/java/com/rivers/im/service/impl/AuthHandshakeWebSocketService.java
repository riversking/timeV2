package com.rivers.im.service.impl;

import com.rivers.im.service.IWsTicketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.server.support.HandshakeWebSocketService;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
@NullMarked
public class AuthHandshakeWebSocketService extends HandshakeWebSocketService {

    private final IWsTicketService wsTicketService;

    @Override
    public Mono<Void> handleRequest(ServerWebExchange exchange, WebSocketHandler handler) {
        String ticket = extractParam(exchange, "ticket");
        if (ticket.isBlank()) {
            log.warn("❌ WS握手拒绝: 缺少 ticket");
            return rejectHandshake(exchange, HttpStatus.BAD_REQUEST);
        }
        // 🌟 核心修复：将 switchIfEmpty 放在 flatMap 之前！
        Mono<String> userIdMono = wsTicketService.consumeTicket(ticket)
                .timeout(Duration.ofSeconds(5))
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("❌ WS握手拒绝: ticket 无效或已过期, ticket={}", ticket);
                    return rejectHandshake(exchange, HttpStatus.UNAUTHORIZED).then(Mono.empty());
                }))
                .onErrorResume(e -> {
                    log.error("❌ WS握手鉴权异常: {}", e.getMessage());
                    return rejectHandshake(exchange, HttpStatus.UNAUTHORIZED).then(Mono.empty());
                });
        // 只有当 userIdMono 有值时，才会执行这里的 flatMap
        return userIdMono.flatMap(userId -> {
            // 注意：之前提到过，exchange.getAttributes() 不会传递给 WebSocketSession
            // 如果后续 Handler 需要 userId，建议使用装饰器模式（见下方补充）
            WebSocketHandler decoratedHandler = session -> {
                session.getAttributes().put("userId", userId);
                return handler.handle(session);
            };
            log.info("✅ WS握手成功: userId={}", userId);
            return super.handleRequest(exchange, decoratedHandler);
        });
    }

    /**
     * 优雅地拒绝握手，避免抛出 RuntimeException 导致 already committed 报错
     */
    private Mono<Void> rejectHandshake(ServerWebExchange exchange, HttpStatus status) {
        ServerHttpResponse response = exchange.getResponse();
        if (!response.isCommitted()) {
            response.setStatusCode(status);
            return response.setComplete();
        }
        return Mono.empty();
    }

    private String extractParam(ServerWebExchange exchange, String key) {
        List<String> values = exchange.getRequest().getQueryParams().get(key);
        return (values != null && !values.isEmpty()) ? values.getFirst() : "";
    }
}