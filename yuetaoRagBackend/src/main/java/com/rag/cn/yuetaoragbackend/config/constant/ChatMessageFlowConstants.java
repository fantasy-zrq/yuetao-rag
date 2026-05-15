package com.rag.cn.yuetaoragbackend.config.constant;

/**
 * ChatMessageServiceImpl 对话链路使用的稳定业务常量。
 * 这里集中维护路由、SSE、追踪和文案约束，避免魔法值散落在流程代码中。
 */
public final class ChatMessageFlowConstants {

    private ChatMessageFlowConstants() {
    }

    public static final long STREAM_TIMEOUT_MILLIS = 180_000L;
    public static final double LEAF_INTENT_SCORE_THRESHOLD = 0.5D;
    public static final int ROUTE_TOP_KB_LIMIT = 3;
    public static final long TRACE_CANCELLED_LATENCY_MILLIS = 1L;
    public static final long MESSAGE_SEQUENCE_INCREMENT = 2L;

    public static final int QUESTION_LOG_MAX_LENGTH = 80;
    public static final int REWRITE_LOG_MAX_LENGTH = 120;
    public static final int REWRITE_TRACE_MAX_LENGTH = 240;
    public static final int CITATION_SNIPPET_MAX_LENGTH = 120;
    public static final int TRACE_PAYLOAD_MAX_LENGTH = 512;
    public static final int TRACE_PAYLOAD_ITEM_LIMIT = 3;
    public static final int TRACE_PAYLOAD_TEXT_MAX_LENGTH = 24;
    public static final int TRACE_PAYLOAD_QUESTION_MAX_LENGTH = 120;

    public static final String ROLE_USER = "USER";
    public static final String ROLE_ASSISTANT = "ASSISTANT";
    public static final String INTENT_TYPE_CHITCHAT = "CHITCHAT";
    public static final String INTENT_TYPE_KB_QA = "KB_QA";
    public static final String STATIC_REFUSAL_ANSWER = "当前知识库中没有该方面的内容，暂时无法回答这个问题。";
    public static final String STATIC_CANDIDATE_ID = "static";
    public static final String MESSAGE_SEQUENCE_KEY_PREFIX = "chat:message:sequence:";

    public static final String TRACE_STAGE_REWRITE = "REWRITE";
    public static final String TRACE_STAGE_INTENT = "INTENT";
    public static final String TRACE_STAGE_INTENT_SCORE = "INTENT_SCORE";
    public static final String TRACE_STAGE_RETRIEVE = "RETRIEVE";
    public static final String TRACE_STAGE_RERANK = "RERANK";
    public static final String TRACE_STAGE_GENERATE = "GENERATE";
    public static final String TRACE_STAGE_STREAM_CANDIDATE = "STREAM_CANDIDATE";

    public static final String TRACE_STATUS_SUCCESS = "SUCCESS";
    public static final String TRACE_STATUS_FAILED = "FAILED";
    public static final String TRACE_STATUS_SKIPPED = "SKIPPED";
    public static final String TRACE_STATUS_CANCELLED = "CANCELLED";

    public static final String TRACE_ROUTE_SOURCE_LEAF_SCORE = "LEAF_SCORE";
    public static final String TRACE_ROUTE_SOURCE_MODEL_CLASSIFIER = "MODEL_CLASSIFIER";
    public static final String TRACE_SCOPE_KB_SCOPED = "KB_SCOPED";
    public static final String TRACE_SCOPE_GLOBAL = "GLOBAL";
    public static final String TRACE_SCOPE_GLOBAL_FALLBACK = "GLOBAL_FALLBACK";
    public static final String TRACE_ANSWER_SOURCE_MODEL_DIRECT = "MODEL_DIRECT";
    public static final String TRACE_ANSWER_SOURCE_SYSTEM_PROMPT = "SYSTEM_PROMPT";
    public static final String TRACE_ANSWER_SOURCE_RAG_MODEL = "RAG_MODEL";
    public static final String TRACE_ANSWER_SOURCE_STREAM_MODEL = "STREAM_MODEL";
    public static final String TRACE_ANSWER_SOURCE_STATIC_REFUSAL = "STATIC_REFUSAL";
    public static final String TRACE_TERMINATION_USER_STOP = "USER_STOP";

    public static final String STREAM_FAILURE_IDLE_TIMEOUT = "idle-timeout";
    public static final String STREAM_FAILURE_FIRST_TOKEN_TIMEOUT = "first-token-timeout";
    public static final String STREAM_FAILURE_STREAM_ERROR = "stream-error";
    public static final String STREAM_FAILURE_RERANK_EMPTY = "rerank-empty";
}
