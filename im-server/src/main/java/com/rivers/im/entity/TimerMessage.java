package com.rivers.im.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 消息表
 * </p>
 *
 * @author 
 * @since 2026-04-28
 */
@Getter
@Setter
@Table("timer_message")
public class TimerMessage implements Serializable{

    @Serial
    private static final long serialVersionUID = -4180944703284790034L;

    @Id
    private Long id;

    /**
     * 发送者ID
     */
    @Column("from_user_id")
    private Long fromUserId;

    /**
     * 接收者ID(私聊)
     */
    @Column("to_user_id")
    private Long toUserId;

    /**
     * 群组ID(群聊)
     */
    @Column("group_id")
    private Long groupId;

    /**
     * 消息类型: 1-文本, 2-图片, 3-文件, 4-语音, 5-视频
     */
    @Column("message_type")
    private Integer messageType;

    /**
     * 消息内容
     */
    @Column("content")
    private String content;

    /**
     * 文件URL(图片/文件/语音/视频)
     */
    @Column("file_url")
    private String fileUrl;

    /**
     * 阅读状态: 0-未读, 1-已读
     */
    @Column("read_status")
    private Byte readStatus;

    /**
     * 发送时间
     */
    @Column("sent_time")
    private LocalDateTime sentTime;

    /**
     * 创建人
     */
    @Column("create_user")
    private String createUser;

    /**
     * 创建时间
     */
    @Column("create_time")
    private LocalDateTime createTime;

    /**
     * 修改人
     */
    @Column("update_user")
    private String updateUser;

    /**
     * 修改时间
     */
    @Column("update_time")
    private LocalDateTime updateTime;

}
