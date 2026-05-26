package com.rivers.im.manage;

import com.rivers.im.context.ConnectionContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class LocalSessionManager {

    // connId -> Context
    private final Map<String, ConnectionContext> localConnections = new ConcurrentHashMap<>();

    public void register(String connId, ConnectionContext ctx) {
        localConnections.put(connId, ctx);
    }

    public void unregister(String connId) {
        ConnectionContext ctx = localConnections.remove(connId);
        if (ctx != null) {
            ctx.getOutboundSink().tryEmitComplete();
        }
    }

    public ConnectionContext get(String connId) {
        return localConnections.get(connId);
    }

    /**
     * 线程安全的本地推送
     */
    public void pushToLocal(String connId, String jsonMsg) {
        ConnectionContext ctx = localConnections.get(connId);
        if (ctx != null && ctx.getSession().isOpen()) {
            ctx.push(jsonMsg);
        } else {
            log.debug("⚠️ 本地连接不存在或已关闭: {}", connId);
        }
    }
}