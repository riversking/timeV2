package com.rivers.im.constant;

/**
 * 跨服推送通道常量：Redis Stream 替代 Pub/Sub。
 * 每个节点拥有自己的流 ws:node:{serverId}，其他节点向其 XADD。
 */
public final class CrossServerChannel {

    public static final String STREAM_PREFIX = "ws:node:";
    public static final String GROUP_NAME = "ws-push";
    public static final long MAX_LEN = 10_000;

    private CrossServerChannel() {
    }

    public static String streamKeyOf(String serverId) {
        return STREAM_PREFIX + serverId;
    }
}