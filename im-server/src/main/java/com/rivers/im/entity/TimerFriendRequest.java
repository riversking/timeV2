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

    @Column("user_id")
    private String userId;

    @Column("opponent_id")
    private String opponentId;

    @Column("direction")
    private Integer direction;

    @Column("status")
    private Integer status;

    @Column("message")
    private String message;

    @Column("relation_id")
    private Long relationId;

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

    @Getter
    public enum Status {
        PENDING(0, "待处理"),
        ACCEPTED(1, "已同意"),
        REJECTED(2, "已拒绝");

        private final int code;
        private final String desc;

        Status(int code, String desc) {
            this.code = code;
            this.desc = desc;
        }

        public static Status of(int code) {
            for (Status value : Status.values()) {
                if (value.code == code) {
                    return value;
                }
            }
            return null;
        }

    }

    @Getter
    public enum Direction {
        SENT(1, "我发出的"),
        RECEIVED(2, "我收到的");

        private final int code;
        private final String desc;

        Direction(int code, String desc) {
            this.code = code;
            this.desc = desc;
        }

        public static Direction of(int code) {
            for (Direction value : Direction.values()) {
                if (value.code == code) {
                    return value;
                }
            }
            return null;
        }
    }
}
