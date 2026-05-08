package com.rag.cn.yuetaoragbackend.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.rag.cn.yuetaoragbackend.dao.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * @author zrq
 * 2026/04/22 14:45
 */
@Data
@TableName("t_chat_message")
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class ChatMessageDO extends BaseDO {

    /** 所属会话ID。 */
    private Long sessionId;

    /** 消息归属用户ID。 */
    private Long userId;

    /** 消息角色。 */
    private String role;

    /** 消息内容。 */
    private String content;

    /** 消息内容类型。 */
    private String contentType;

    /** 会话内消息顺序号。 */
    private Integer sequenceNo;

    /** 链路追踪ID。 */
    private String traceId;

    /** 模型提供商。 */
    private String modelProvider;

    /** 模型名称。 */
    private String modelName;

    /** 思考内容。 */
    private String thinkingContent;

    /** 思考耗时（毫秒）。 */
    private Long thinkingDurationMs;
}
