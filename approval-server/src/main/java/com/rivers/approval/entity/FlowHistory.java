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
@Table("flow_history")
public class FlowHistory {

    @Id
    private Long id;

    /**
     * 关联流程实例ID
     */
    @Column("instance_id")
    private Long instanceId;

    /**
     * 关联节点实例ID（可为空，用于流程级事件）
     */
    @Column("node_instance_id")
    private Long nodeInstanceId;

    /**
     * 关联任务ID（可为空）
     */
    @Column("task_id")
    private Long taskId;

    /**
     * 事件类型
     * INSTANCE_STARTED    — 流程发起
     * INSTANCE_COMPLETED  — 流程完成
     * INSTANCE_TERMINATED — 流程终止
     * NODE_STARTED        — 节点开始
     * NODE_COMPLETED      — 节点完成
     * TASK_CREATED        — 任务创建
     * TASK_CLAIMED        — 任务认领
     * TASK_COMPLETED      — 任务完成
     * TASK_CANCELLED      — 任务取消
     * GATEWAY_EVALUATED   — 网关条件评估
     */
    @Column("event_type")
    private String eventType;

    /**
     * 操作人ID
     */
    @Column("operator_id")
    private String operatorId;

    /**
     * 操作人姓名
     */
    @Column("operator_name")
    private String operatorName;

    /**
     * 事件详情（JSON字符串，含变更前后的快照）
     */
    private String detail;

    /**
     * 备注
     */
    private String remark;

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
