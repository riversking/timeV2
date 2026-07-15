package com.rivers.approval.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("flow_rule")
public class FlowRule {

    @Id
    private Long id;

    /**
     * 规则编码（唯一业务键）
     */
    @Column("rule_code")
    private String ruleCode;

    /**
     * 规则名称
     */
    private String name;

    /**
     * 规则描述
     */
    private String description;

    /**
     * 规则类型
     * CONDITION  — 条件路由（用于排他网关分支判断）
     * ACTION     — 动作执行
     * VALIDATION — 校验
     */
    @Column("rule_type")
    private String ruleType;

    /**
     * 关联流程定义ID（可为空，支持全局规则）
     */
    @Column("definition_id")
    private Long definitionId;

    /**
     * 关联节点ID（网关节点条件规则）
     */
    @Column("node_id")
    private String nodeId;

    /**
     * 规则配置（JSON字符串，SpEL条件链）
     * 结构: [
     * {
     * "condition":      "#amount > 5000",     // SpEL条件表达式
     * "action":         "SET_APPROVER_DIRECTOR", // 动作标识
     * "breakOnMatch":   true,                 // 命中即停
     * "priority":       10,                   // 优先级
     * "inputMapping":   { ... },              // 输入变量映射
     * "outputMapping":  { ... }               // 输出变量映射
     * }
     * ]
     */
    @Column("rule_config")
    private String ruleConfig;

    /**
     * 优先级（数值越大优先级越高）
     */
    private Integer priority;

    /**
     * 是否启用: 0-禁用, 1-启用
     */
    private Integer enabled;

    // ========== 审计字段 ==========
    @Column("create_user")
    private String createUser;

    @Column("create_time")
    private LocalDateTime createTime;

    @Column("update_user")
    private String updateUser;

    @Column("update_time")
    private LocalDateTime updateTime;

    @Column("is_deleted")
    private Integer isDeleted;
}
