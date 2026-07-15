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
@Table("flow_task")
public class FlowTask {

    @Id
    private Long id;

    /**
     * 关联流程实例ID
     */
    @Column("instance_id")
    private Long instanceId;

    /**
     * 关联节点实例ID
     */
    @Column("node_instance_id")
    private Long nodeInstanceId;

    /**
     * 任务编号（业务流水号）
     */
    @Column("task_no")
    private String taskNo;

    /**
     * 任务名称（继承节点名称）
     */
    @Column("task_name")
    private String taskName;

    /**
     * 任务状态
     * PENDING     — 待认领
     * CLAIMED     — 已认领
     * COMPLETED   — 已完成
     * CANCELLED   — 已取消
     * TRANSFERRED — 已转交
     */
    private String status;

    /**
     * 指定处理人（如为空则需认领）
     */
    private String assignee;

    /**
     * 候选人列表（JSON数组字符串，认领池依据）
     */
    @Column("candidate_users")
    private String candidateUsers;

    /**
     * 认领人
     */
    @Column("claimed_by")
    private String claimedBy;

    /**
     * 认领时间
     */
    @Column("claimed_time")
    private LocalDateTime claimedTime;

    /**
     * 完成人
     */
    @Column("completed_by")
    private String completedBy;

    /**
     * 完成时间
     */
    @Column("completed_time")
    private LocalDateTime completedTime;

    /**
     * 处理结果
     * APPROVED — 通过
     * REJECTED — 驳回
     */
    private String result;

    /**
     * 审批意见
     */
    private String comment;

    /**
     * 截止时间
     */
    @Column("due_time")
    private LocalDateTime dueTime;

    /**
     * 优先级
     * 0 — 普通
     * 1 — 紧急
     * 2 — 非常紧急
     */
    private Integer priority;

    /**
     * 前驱任务ID（转交场景追溯）
     */
    @Column("prev_task_id")
    private Long prevTaskId;

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
