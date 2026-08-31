package com.rivers.im.context;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Sinks;

@Slf4j
@Getter
public class ConnectionContext {

    private final WebSocketSession session;
    private final String userId;
    private final Sinks.Many<String> outboundSink;

    public ConnectionContext(WebSocketSession session, String userId) {
        this.session = session;
        this.userId = userId;
        // 多播、带缓冲的背压策略，保证线程安全；
        // tryEmitNext 的 FAIL_FAST 不会自动丢消息，溢出由 push() 显式处理
        this.outboundSink = Sinks.many().multicast().onBackpressureBuffer(1024, false);
    }

    public void push(String json) {
        Sinks.EmitResult result = outboundSink.tryEmitNext(json);
        if (result == Sinks.EmitResult.FAIL_OVERFLOW) {
            // 客户端消费过慢、缓冲已满：关闭连接让其重连，
            // 防止消息无限积压导致内存膨胀（比静默丢消息更可控）
            log.warn("⚠️ 发送缓冲溢出，关闭慢消费者连接: userId={}", userId);
            session.close()
                    .subscribe(null, e -> log.warn("⚠️ 关闭连接失败: userId={}", userId, e));
        }
    }
}