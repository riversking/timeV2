package com.rivers.approval.model;

import java.util.Collections;
import java.util.Map;

/**
 * 流程定义中的单个节点。
 * 从 flow_definition.definition_json 的 nodes[] 反序列化得到。
 */
public record NodeDef(
        String id,
        /* START / END / USER_TASK / EXCLUSIVE_GATEWAY / PARALLEL_GATEWAY */
        String type,
        String name,
        /* 节点配置：候选人表达式、表单字段、网关条件等，类型相关 */
        Map<String, Object> config
) {
    public NodeDef {
        config = config != null ? Collections.unmodifiableMap(config) : Collections.emptyMap();
    }

    /**
     * 便捷取值
     */
    public <T> T config(String key) {
        return (T) config.get(key);
    }
}
