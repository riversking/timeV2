package com.rivers.approval.handle;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 节点处理器注册中心。
 * Spring 自动注入所有 NodeHandler Bean，按 supportedType() 建立索引。
 */
@Component
public class NodeHandlerRegistry {

    private final Map<String, NodeHandler> index;

    public NodeHandlerRegistry(List<NodeHandler> handlers) {
        this.index = handlers.stream()
                .collect(Collectors.toUnmodifiableMap(
                        NodeHandler::supportedType,
                        Function.identity(),
                        (a, b) -> { throw new IllegalStateException(
                                "重复的 NodeHandler: " + a.supportedType()); }
                ));
    }

    /** 按节点类型获取处理器 */
    public NodeHandler get(String nodeType) {
        var handler = index.get(nodeType);
        if (handler == null) {
            throw new IllegalArgumentException("未找到节点类型对应的处理器: " + nodeType);
        }
        return handler;
    }
}
