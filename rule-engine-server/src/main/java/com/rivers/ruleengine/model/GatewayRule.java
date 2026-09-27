package com.rivers.ruleengine.model;

import java.util.Map;

/**
 * 排他网关内嵌规则（流程定义网关节点 config.rules 数组元素）。
 * 直接配置在流程定义中，运行时直接计算，无需入库。
 */
public record GatewayRule(
        /* SpEL 条件表达式，命中即路由 */
        String condition,
        /* 命中后的目标节点 id（必须属于该网关出边目标集合） */
        String targetNodeId,
        /* 优先级（数值越大越先求值；缺省按 0） */
        Integer priority,
        /* 命中后写入流程变量的输出映射（可为 null） */
        Map<String, Object> outputMapping
) {
}
