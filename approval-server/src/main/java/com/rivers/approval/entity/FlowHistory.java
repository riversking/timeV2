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
     * 流程实例编号
     */
    @Column("instance_no")
    private String instanceNo;

    /**
     * 关联节点实例ID（流程级事件为空）
     */
    @Column("node_instance_id")
    private Long nodeInstanceId;

    /**
     * 节点ID
     */
    @Column("node_id")
    private String nodeId;

    /**
     * 节点名称
     */
    @Column("node_name")
    private String nodeName;

    /**
     * 节点类型
     */
    @Column("node_type")
    private String nodeType;

    /**
     * 关联任务ID（流程级/节点级事件为空）
     */
    @Column("task_id")
    private Long taskId;

    /**
     * 任务编号
     */
    @Column("task_no")
    private String taskNo;

    /**
     * 任务名称
     */
    @Column("task_name")
    private String taskName;

    /**
     * 办理人
     */
    private String assignee;

    /**
     * 转交目标人
     */
    @Column("target_assignee")
    private String targetAssignee;

    /**
     * 审批结果（APPROVED / REJECTED）
     */
    private String result;

    /**
     * 审批意见
     */
    private String opinion;

    /**
     * 变更前状态
     */
    @Column("from_status")
    private String fromStatus;

    /**
     * 变更后状态
     */
    @Column("to_status")
    private String toStatus;

    /**
     * 事件类型
     * INSTANCE_STARTED / INSTANCE_COMPLETED / INSTANCE_TERMINATED
     * NODE_STARTED / NODE_COMPLETED
     * TASK_CREATED / TASK_CLAIMED / TASK_COMPLETED / TASK_CANCELLED / TASK_TRANSFERRED
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
     * 人读摘要（如：完成任务 结果:APPROVED 意见:同意）
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