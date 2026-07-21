package com.rivers.approval.event;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * 流程事件公共元数据，被所有事件类型内嵌复用。
 */
public record FlowEventMetadata(
        Long instanceId,
        String instanceNo,
        String eventType,
        LocalDateTime timestamp,
        String operatorId,
        String operatorName,
        Map<String, Object> variables) {

    /**
     * 紧凑构造器：校验必填字段 + 防御性拷贝
     */
    public FlowEventMetadata {
        Objects.requireNonNull(instanceId, "instanceId 不能为空");
        Objects.requireNonNull(instanceNo, "instanceNo 不能为空");
        Objects.requireNonNull(eventType, "eventType 不能为空");
        timestamp = timestamp != null ? timestamp : LocalDateTime.now(ZoneId.systemDefault());
        operatorId = operatorId != null ? operatorId : "SYSTEM";
        operatorName = operatorName != null ? operatorName : "";
        variables = variables != null
                ? Collections.unmodifiableMap(variables)
                : Collections.emptyMap();
    }

    /**
     * 便捷工厂：时间默认 now，操作人默认 SYSTEM
     */
    public static FlowEventMetadata of(Long instanceId, String instanceNo, String eventType) {
        return new FlowEventMetadata(instanceId, instanceNo, eventType,
                LocalDateTime.now(ZoneId.systemDefault()), "SYSTEM", "", Collections.emptyMap());
    }

    /**
     * 返回一个修改了 variables 的新副本（不可变更新）
     */
    public FlowEventMetadata withVariables(Map<String, Object> newVars) {
        return new FlowEventMetadata(instanceId, instanceNo, eventType,
                timestamp, operatorId, operatorName, newVars);
    }

    /**
     * 返回一个修改了 operatorId 的新副本
     */
    public FlowEventMetadata withOperator(String operatorId, String operatorName) {
        return new FlowEventMetadata(instanceId, instanceNo, eventType,
                timestamp, operatorId, operatorName, variables);
    }
}
