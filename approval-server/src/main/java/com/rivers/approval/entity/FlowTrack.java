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
@Table("flow_track")
public class FlowTrack {

    @Id
    private Long id;

    @Column("instance_id")
    private Long instanceId;

    @Column("instance_no")
    private String instanceNo;

    @Column("node_instance_id")
    private Long nodeInstanceId;

    /**
     * STARTED / PENDING / CLAIMED / TRANSFERRED / APPROVED / REJECTED / RETURNED
     */
    @Column("track_type")
    private String trackType;

    @Column("node_name")
    private String nodeName;

    private String assignee;

    @Column("next_assignee")
    private String nextAssignee;

    @Column("task_time")
    private LocalDateTime taskTime;

    @Column("action_time")
    private LocalDateTime actionTime;

    private String opinion;

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