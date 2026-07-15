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
@Table("flow_node_instance")
public class FlowNodeInstance {

    @Id
    private Long id;

    /**
     * 关联流程实例ID
     */
    @Column("instance_id")
    private Long instanceId;

    /**
     * 节点ID（对应 DSL 中 node.id）
     */
    @Column("node_id")
    private String nodeId;

    /**
     * 节点名称（如 部门经理审批）
     */
    @Column("node_name")
    private String nodeName;

    /**
     * 节点类型
     * START              — 开始
     * END                — 结束
     * USER_TASK          — 用户任务
     * EXCLUSIVE_GATEWAY  — 排他网关
     * PARALLEL_GATEWAY   — 并行网关
     */
    @Column("node_type")
    private String nodeType;

    /**
     * 节点状态
     * PENDING   — 待处理
     * ACTIVE    — 执行中
     * COMPLETED — 已完成
     * SKIPPED   — 已跳过
     */
    private String status;

    /**
     * 指定处理人（USER_TASK节点使用）
     */
    private String assignee;

    /**
     * 候选人列表（USER_TASK节点使用，JSON数组字符串）
     */
    @Column("candidate_users")
    private String candidateUsers;

    /**
     * 节点输入变量（进入节点时的上下文快照，JSON字符串）
     */
    @Column("input_variables")
    private String inputVariables;

    /**
     * 节点输出变量（节点完成后的变更，JSON字符串）
     */
    @Column("output_variables")
    private String outputVariables;

    /**
     * 父节点实例ID（并行网关 Fork 出来的子分支使用）
     */
    @Column("parent_node_instance_id")
    private Long parentNodeInstanceId;

    /**
     * Fork分支总数（并行网关 Fork 节点记录）
     */
    @Column("fork_count")
    private Integer forkCount;

    /**
     * 已完成 Join 的分支数（Join 节点计数用）
     */
    @Column("join_count")
    private Integer joinCount;

    /**
     * 节点开始时间
     */
    @Column("start_time")
    private LocalDateTime startTime;

    /**
     * 节点结束时间
     */
    @Column("end_time")
    private LocalDateTime endTime;

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
