package com.rivers.im.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class ChatMessage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1119986564303264334L;

    private String msgId;

    /**
     * 消息类型：text(文本)、image(图片)、file(文件)、voice(语音)、video(视频)
     */
    private String type;

    /**
     * 消息内容（文本内容或文件URL）
     */
    private String content;

    /**
     * 扩展内容（JSON格式，用于存储图片宽高、文件大小等额外信息）
     */
    private String extraData;

    // ========== 发送与接收 ==========
    /**
     * 发送者用户ID
     */
    private String from;

    /**
     * 接收者用户ID（群聊时为"-1"或群组ID）
     */
    private String to;

    /**
     * 接收者类型：user(单聊)、group(群聊)、system(系统)
     */
    private String toType;

    // ========== 时间与状态 ==========
    /**
     * 消息发送时间戳（毫秒）
     */
    private Long timestamp;

    /**
     * 消息状态：sending(发送中)、sent(已发送)、delivered(已送达)、read(已读)
     */
    private String status;

    /**
     * 是否撤回
     */
    private Boolean recalled;

    // ========== 附加信息 ==========
    /**
     * 客户端生成的消息临时ID（用于消息去重和确认）
     */
    private String clientMsgId;

    /**
     * 消息序列号（用于排序）
     */
    private Long sequence;

    private boolean isRead;
}
