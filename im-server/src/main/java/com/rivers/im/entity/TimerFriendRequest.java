package com.rivers.im.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("timer_friend_request")
public class TimerFriendRequest {

    @Id
    private Long id;

    @Column("request_user_id")
    private String requestUserId; // 请求方

    @Column("target_user_id")
    private String targetUserId; // 目标用户

    @Column("remark")
    private String remark; // 备注

    @Column("request_msg")
    private String requestMsg; // 验证消息

    @Column("status")
    private Integer status; // 0-待处理, 1-已接受, 2-已拒绝

    @Column("create_time")
    private LocalDateTime createTime;

    @Column("update_time")
    private LocalDateTime updateTime;

    @Column("create_user")
    private String createUser;

    @Column("update_user")
    private String updateUser;

    @Column("is_deleted")
    private Integer isDeleted;

    /**
     * 状态枚举
     */
    @Getter
    public enum Status {
        PENDING(0, "待处理"),
        ACCEPTED(1, "已接受"),
        REJECTED(2, "已拒绝");

        private final int code;
        private final String desc;

        Status(int code, String desc) {
            this.code = code;
            this.desc = desc;
        }

    }
}