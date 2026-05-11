package com.rag.cn.yuetaoragbackend.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @author zrq
 * 2026/04/22 15:05
 */
@Data
@Accessors(chain = true)
public class CreateChatMessageReq {

    /** 所属会话ID。 */
    @NotNull(message = "会话ID不能为空")
    private Long sessionId;

    /** 消息角色。 */
    @NotBlank(message = "消息角色不能为空")
    private String role;

    /** 消息内容。 */
    @NotBlank(message = "消息内容不能为空")
    private String content;

    /** 消息内容类型。 */
    private String contentType;

    /** 会话内顺序号。 */
    @NotNull(message = "消息顺序号不能为空")
    private Integer sequenceNo;

    /** 链路追踪ID。 */
    private String traceId;

    /** 模型提供商。 */
    private String modelProvider;

    /** 模型名称。 */
    private String modelName;
}
