package com.rivers.im.context;

import lombok.Getter;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Sinks;

@Getter
public class ConnectionContext {

    private final WebSocketSession session;
    private final String userId;
    private final Sinks.Many<String> outboundSink;

    public ConnectionContext(WebSocketSession session, String userId) {
        this.session = session;
        this.userId = userId;
        // 多播、带缓冲的背压策略，保证线程安全
        this.outboundSink = Sinks.many().multicast().onBackpressureBuffer(1024);
    }

    public void push(String json) {
        outboundSink.tryEmitNext(json);
    }
}