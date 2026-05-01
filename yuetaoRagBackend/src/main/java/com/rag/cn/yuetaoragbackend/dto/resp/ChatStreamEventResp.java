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

    private String event;

    private String traceId;

    private Long sessionId;

    private String candidateId;

    private Integer attemptNo;

    private String content;

    private String reason;

    private String code;

    private String message;

    private Long assistantMessageId;

    private Boolean finished;

    private List<ChatStreamCitationResp> citations;

    public static ChatStreamEventResp messageStart(String traceId, Long sessionId, String candidateId, Integer attemptNo) {
        return new ChatStreamEventResp()
                .setEvent(ChatStreamEventTypeEnum.MESSAGE_START.getCode())
                .setTraceId(traceId)
                .setSessionId(sessionId)
                .setCandidateId(candidateId)
                .setAttemptNo(attemptNo);
    }

    public static ChatStreamEventResp delta(String traceId, Long sessionId, String content) {
        return new ChatStreamEventResp()
                .setEvent(ChatStreamEventTypeEnum.DELTA.getCode())
                .setTraceId(traceId)
                .setSessionId(sessionId)
                .setContent(content);
    }

    public static ChatStreamEventResp citation(String traceId, Long sessionId, List<ChatStreamCitationResp> citations) {
        return new ChatStreamEventResp()
                .setEvent(ChatStreamEventTypeEnum.CITATION.getCode())
                .setTraceId(traceId)
                .setSessionId(sessionId)
                .setCitations(citations);
    }

    public static ChatStreamEventResp reset(String traceId, Long sessionId, String candidateId, String reason) {
        return new ChatStreamEventResp()
                .setEvent(ChatStreamEventTypeEnum.RESET.getCode())
                .setTraceId(traceId)
                .setSessionId(sessionId)
                .setCandidateId(candidateId)
                .setReason(reason);
    }

    public static ChatStreamEventResp messageEnd(String traceId, Long sessionId, Long assistantMessageId) {
        return new ChatStreamEventResp()
                .setEvent(ChatStreamEventTypeEnum.MESSAGE_END.getCode())
                .setTraceId(traceId)
                .setSessionId(sessionId)
                .setAssistantMessageId(assistantMessageId)
                .setFinished(Boolean.TRUE);
    }

    public static ChatStreamEventResp error(String traceId, Long sessionId, String code, String message) {
        return new ChatStreamEventResp()
                .setEvent(ChatStreamEventTypeEnum.ERROR.getCode())
                .setTraceId(traceId)
                .setSessionId(sessionId)
                .setCode(code)
                .setMessage(message);
    }
}
