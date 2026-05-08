package com.rag.cn.yuetaoragbackend.dto.resp;

import com.rag.cn.yuetaoragbackend.config.enums.ChatStreamEventTypeEnum;
import java.util.List;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @author zrq
 * 2026/04/29 23:30
 */
@Data
@Accessors(chain = true)
public class ChatStreamEventResp {

    /** 事件类型标识，如 message_start、delta、thinking_delta、citation、reset、message_end、error。 */
    private String event;

    /** 链路追踪ID。 */
    private String traceId;

    /** 所属会话ID。 */
    private Long sessionId;

    /** 当前使用的候选模型ID。 */
    private String candidateId;

    /** 候选模型尝试序号，从1开始递增。 */
    private Integer attemptNo;

    /** 事件携带的文本内容。 */
    private String content;

    /** 事件附加原因说明（如重置原因）。 */
    private String reason;

    /** 错误码。 */
    private String code;

    /** 错误信息。 */
    private String message;

    /** 助手消息持久化后的ID。 */
    private Long assistantMessageId;

    /** 流式是否已结束。 */
    private Boolean finished;

    /** 引用列表。 */
    private List<ChatStreamCitationResp> citations;

    /**
     * 事件内容类型，用于区分 thinking 和 response。
     */
    private String type;

    /** 创建消息开始事件。 */
    public static ChatStreamEventResp messageStart(String traceId, Long sessionId, String candidateId, Integer attemptNo) {
        return new ChatStreamEventResp()
                .setEvent(ChatStreamEventTypeEnum.MESSAGE_START.getCode())
                .setTraceId(traceId)
                .setSessionId(sessionId)
                .setCandidateId(candidateId)
                .setAttemptNo(attemptNo);
    }

    /** 创建内容增量事件。 */
    public static ChatStreamEventResp delta(String traceId, Long sessionId, String content) {
        return new ChatStreamEventResp()
                .setEvent(ChatStreamEventTypeEnum.DELTA.getCode())
                .setTraceId(traceId)
                .setSessionId(sessionId)
                .setContent(content)
                .setType("response");
    }

    /** 创建思考过程增量事件。 */
    public static ChatStreamEventResp thinkingDelta(String traceId, Long sessionId, String content) {
        return new ChatStreamEventResp()
                .setEvent(ChatStreamEventTypeEnum.THINKING_DELTA.getCode())
                .setTraceId(traceId)
                .setSessionId(sessionId)
                .setContent(content)
                .setType("think");
    }

    /** 创建引用列表事件。 */
    public static ChatStreamEventResp citation(String traceId, Long sessionId, List<ChatStreamCitationResp> citations) {
        return new ChatStreamEventResp()
                .setEvent(ChatStreamEventTypeEnum.CITATION.getCode())
                .setTraceId(traceId)
                .setSessionId(sessionId)
                .setCitations(citations);
    }

    /** 创建流式重置事件，用于候选模型切换时通知前端清空已输出内容。 */
    public static ChatStreamEventResp reset(String traceId, Long sessionId, String candidateId, String reason) {
        return new ChatStreamEventResp()
                .setEvent(ChatStreamEventTypeEnum.RESET.getCode())
                .setTraceId(traceId)
                .setSessionId(sessionId)
                .setCandidateId(candidateId)
                .setReason(reason);
    }

    /** 创建消息结束事件。 */
    public static ChatStreamEventResp messageEnd(String traceId, Long sessionId, Long assistantMessageId) {
        return new ChatStreamEventResp()
                .setEvent(ChatStreamEventTypeEnum.MESSAGE_END.getCode())
                .setTraceId(traceId)
                .setSessionId(sessionId)
                .setAssistantMessageId(assistantMessageId)
                .setFinished(Boolean.TRUE);
    }

    /** 创建错误事件。 */
    public static ChatStreamEventResp error(String traceId, Long sessionId, String code, String message) {
        return new ChatStreamEventResp()
                .setEvent(ChatStreamEventTypeEnum.ERROR.getCode())
                .setTraceId(traceId)
                .setSessionId(sessionId)
                .setCode(code)
                .setMessage(message);
    }
}
