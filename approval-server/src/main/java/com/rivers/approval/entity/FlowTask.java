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
     * PENDING     — 待办理
     * CLAIMED     — 已认领
     * WAITING     — 串签等待中（尚未轮到）
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
     * 认领时间
     */
    @Column("claimed_time")
    private LocalDateTime claimedTime;

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
     * 任务模式
     * CLAIM      — 需领单（默认，先领先得）
     * ANY_ONE    — 并签1人：无需领单，任一人办理即节点通过
     * ALL        — 并签多人：无需领单，全部办理后节点通过
     * SEQUENTIAL — 串签所有人：无需领单，按序逐个办理
     */
    @Column("task_mode")
    private String taskMode;

    /**
     * 串签顺序号（从 1 开始，SEQUENTIAL 模式使用）
     */
    private Integer seq;

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