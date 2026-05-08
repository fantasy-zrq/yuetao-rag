package com.rag.cn.yuetaoragbackend.dto.req;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @author zrq
 * 2026/04/29 15:40
 */
@Data
@Accessors(chain = true)
public class ChatReq {

    /**
     * 所属会话ID。
     */
    private Long sessionId;

    /**
     * 发起用户ID。
     */
    private Long userId;

    /**
     * 用户提问内容。
     */
    private String message;

    /**
     * 可选的链路追踪ID。
     */
    private String traceId;

    /**
     * 是否开启深度思考。
     */
    private Boolean deepThinking;
}
