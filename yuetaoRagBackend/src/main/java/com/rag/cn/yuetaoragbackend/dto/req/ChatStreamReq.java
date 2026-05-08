package com.rag.cn.yuetaoragbackend.dto.req;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @author zrq
 * 2026/04/29 23:30
 */
@Data
@Accessors(chain = true)
public class ChatStreamReq {

    /**
     * 所属会话ID。
     */
    private Long sessionId;

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
