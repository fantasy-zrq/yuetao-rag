package com.rag.cn.yuetaoragbackend.dto.resp;

import java.util.List;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @author zrq
 * 2026/04/29 15:40
 */
@Data
@Accessors(chain = true)
public class ChatResp {

    /** 所属会话ID。 */
    private Long sessionId;

    /** 用户消息ID。 */
    private Long userMessageId;

    /** 助手消息ID。 */
    private Long assistantMessageId;

    /** 链路追踪ID。 */
    private String traceId;

    /** 意图识别结果。 */
    private String intentType;

    /** 是否命中知识库证据。 */
    private Boolean knowledgeHit;

    /** Query 改写结果。 */
    private String rewrittenQuery;

    /** 最终回答内容。 */
    private String answer;

    /** 引用列表。 */
    private List<ChatCitationResp> citations;
}
