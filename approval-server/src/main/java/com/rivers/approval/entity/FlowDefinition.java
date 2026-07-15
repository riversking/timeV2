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
@Table("flow_definition")
public class FlowDefinition {

    @Id
    private Long id;

    /**
     * 流程定义标识（唯一业务键，如 leave-apply）
     */
    @Column("definition_key")
    private String definitionKey;

    /**
     * 流程名称（如 请假申请流程）
     */
    private String name;

    /**
     * 流程描述
     */
    private String description;

    /**
     * 版本号（同 key 下递增，用于升级）
     */
    private Integer version;

    /**
     * 状态
     * DRAFT     — 草稿
     * PUBLISHED — 已发布
     * DISABLED  — 已停用
     */
    private String status;

    /**
     * 流程分类（如 OA、业务）
     */
    private String category;

    /**
     * 流程定义 DSL（JSON 字符串）
     * 结构: { "nodes": [...], "edges": [...] }
     * 每个 node: { "id", "type", "name", "config" }
     * type 可选: START / END / USER_TASK / EXCLUSIVE_GATEWAY / PARALLEL_GATEWAY
     * 每个 edge: { "id", "source", "target", "conditionExpression" }
     */
    @Column("definition_json")
    private String definitionJson;

    /**
     * 流程图标URL
     */
    private String icon;

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
