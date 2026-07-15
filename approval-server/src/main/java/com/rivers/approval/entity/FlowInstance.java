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
@Table("flow_instance")
public class FlowInstance {

    @Id
    private Long id;

    /**
     * 流程实例编号（业务流水号，展示用）
     */
    @Column("instance_no")
    private String instanceNo;

    /**
     * 关联流程定义ID
     */
    @Column("definition_id")
    private Long definitionId;

    /**
     * 冗余流程定义标识（便于查询）
     */
    @Column("definition_key")
    private String definitionKey;

    /**
     * 冗余定义版本号
     */
    @Column("definition_version")
    private Integer definitionVersion;

    /**
     * 实例标题（如 张三的请假申请）
     */
    private String title;

    /**
     * 发起人工号/用户名
     */
    private String initiator;

    /**
     * 发起人姓名
     */
    @Column("initiator_name")
    private String initiatorName;

    /**
     * 关联业务主键（如请假单ID，便于与业务系统打通）
     */
    @Column("business_key")
    private String businessKey;

    /**
     * 实例状态
     * RUNNING    — 运行中
     * COMPLETED  — 已完成
     * TERMINATED — 已终止
     */
    private String status;

    /**
     * 流程变量（全局上下文，JSON字符串）
     */
    private String variables;

    /**
     * 当前活跃节点ID列表（并发时可能多个，JSON数组字符串）
     */
    @Column("current_node_ids")
    private String currentNodeIds;

    /**
     * 发起时间
     */
    @Column("start_time")
    private LocalDateTime startTime;

    /**
     * 结束时间
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
