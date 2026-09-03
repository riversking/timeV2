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
@Table("flow_task_done")
public class FlowTaskDone {

    /**
     * 复用原待办任务 id（转交链 prev_task_id 引用基准）
     */
    @Id
    private Long id;

    @Column("instance_id")
    private Long instanceId;

    @Column("node_instance_id")
    private Long nodeInstanceId;

    @Column("task_no")
    private String taskNo;

    @Column("task_name")
    private String taskName;

    /**
     * 终态：COMPLETED / TRANSFERRED / CANCELLED
     */
    private String status;

    /**
     * 实际处理人快照（= 审批人/转交人）
     */
    private String assignee;

    private Integer priority;

    @Column("due_time")
    private LocalDateTime dueTime;

    @Column("claimed_time")
    private LocalDateTime claimedTime;

    /**
     * 终态时间（完成/转交/取消）
     */
    @Column("end_time")
    private LocalDateTime endTime;

    /**
     * APPROVED / REJECTED（仅完成时有值）
     */
    private String result;

    private String comment;

    @Column("prev_task_id")
    private Long prevTaskId;

    @Column("create_user")
    private String createUser;

    @Column("create_time")
    private LocalDateTime createTime;

    @Column("update_user")
    private String updateUser;

    @Column("update_time")
    private LocalDateTime updateTime;
}