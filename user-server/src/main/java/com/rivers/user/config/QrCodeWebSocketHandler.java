package com.rivers.user.config;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class QrCodeWebSocketHandler implements WebSocketHandler {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final Map<String, WebSocketSession> sessionMap = new ConcurrentHashMap<>();

    @Override
    @NullMarked
    public Mono<Void> handle(WebSocketSession session) {
        String qrCodeId = extractQrCodeId(session);
        if (qrCodeId == null || qrCodeId.isEmpty()) {
            log.warn("WebSocket连接缺少二维码ID");
            return session.close();
        }
        sessionMap.put(qrCodeId, session);
        log.info("WebSocket连接建立: {}", qrCodeId);
        return session.send(Mono.just(session.textMessage(createMessage("CONNECTED", null))))
                .then(session.receive()
                        .map(WebSocketMessage::getPayloadAsText)
                        .doOnNext(message -> log.info("收到消息: {}, qrCodeId: {}", message, qrCodeId))
                        .then())
                .doFinally(signalType -> {
                    sessionMap.remove(qrCodeId);
                    log.info("WebSocket连接关闭: {}, signal: {}", qrCodeId, signalType);
                });
    }

    public void sendQrCodeStatus(String qrCodeId, String status, Object data) {
        WebSocketSession session = sessionMap.get(qrCodeId);
        if (session != null && session.isOpen()) {
            try {
                String message = createMessage(status, data);
                session.send(Mono.just(session.textMessage(message))).subscribe();
                log.info("推送二维码状态: {}, status: {}", qrCodeId, status);
            } catch (Exception e) {
                log.error("推送二维码状态失败: {}", qrCodeId, e);
            }
        } else {
            log.warn("WebSocket会话不存在或已关闭: {}", qrCodeId);
        }
    }

    private String extractQrCodeId(WebSocketSession session) {
        String query = session.getHandshakeInfo().getUri().getQuery();
        if (query != null && query.contains("qrCodeId=")) {
            return query.split("qrCodeId=")[1].split("&")[0];
        }
        return null;
    }

    private String createMessage(String status, Object data) {
        try {
            Map<String, Object> message = new java.util.HashMap<>();
            message.put("status", status);
            message.put("data", data);
            message.put("timestamp", System.currentTimeMillis());
            return objectMapper.writeValueAsString(message);
        } catch (Exception e) {
            log.error("创建消息失败", e);
            return "{\"status\":\"ERROR\",\"data\":null}";
        }
    }

    public boolean hasSession(String qrCodeId) {
        WebSocketSession session = sessionMap.get(qrCodeId);
        return session != null && session.isOpen();
    }
}
