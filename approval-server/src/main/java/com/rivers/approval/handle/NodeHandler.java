package com.rivers.approval.handle;

import com.rivers.approval.model.NodeContext;
import reactor.core.publisher.Mono;

/**
 * 节点处理器策略接口。
 * 每种节点类型对应一个实现，由 {@link NodeHandlerRegistry} 按 nodeType 分发。
 */
public interface NodeHandler {

    /** 该处理器支持的节点类型 */
    String supportedType();

    /**
     * 处理当前节点。
     * 完成内部逻辑后由实现自行发布事件（通常是 NodeCompletedEvent），
     * 调用方（FlowExecutor）订阅该事件推进流程。
     */
    Mono<Void> handle(NodeContext ctx);
}
