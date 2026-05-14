package com.rag.cn.yuetaoragbackend.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rag.cn.yuetaoragbackend.config.enums.ChatSessionStatusEnum;
import com.rag.cn.yuetaoragbackend.config.enums.DeleteFlagEnum;
import com.rag.cn.yuetaoragbackend.config.properties.AiProperties;
import com.rag.cn.yuetaoragbackend.service.ChatSessionSummaryService;
import com.rag.cn.yuetaoragbackend.service.IntentNodeService;
import com.rag.cn.yuetaoragbackend.config.properties.MemoryProperties;
import com.rag.cn.yuetaoragbackend.config.properties.TraceProperties;
import com.rag.cn.yuetaoragbackend.config.record.ChatModelInfoRecord;
import com.rag.cn.yuetaoragbackend.config.record.StreamContentRecord;
import com.rag.cn.yuetaoragbackend.framework.context.ApplicationContextHolder;
import com.rag.cn.yuetaoragbackend.framework.context.LoginUser;
import com.rag.cn.yuetaoragbackend.framework.context.UserContext;
import com.rag.cn.yuetaoragbackend.dao.entity.ChatMessageDO;
import com.rag.cn.yuetaoragbackend.dao.entity.ChatSessionDO;
import com.rag.cn.yuetaoragbackend.dao.entity.UserDO;
import com.rag.cn.yuetaoragbackend.dao.mapper.ChatMessageMapper;
import com.rag.cn.yuetaoragbackend.dao.mapper.ChatSessionMapper;
import com.rag.cn.yuetaoragbackend.dao.mapper.QaTraceLogMapper;
import com.rag.cn.yuetaoragbackend.dao.mapper.UserMapper;
import com.rag.cn.yuetaoragbackend.dto.req.ChatStreamReq;
import com.rag.cn.yuetaoragbackend.dto.resp.ChatStreamEventResp;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RedissonClient;
import reactor.core.publisher.Flux;

/**
 * 流式对话熔断场景测试，覆盖普通流式和深度思考流式的熔断、降级、跳过、恢复等场景。
 */
@ExtendWith(MockitoExtension.class)
class StreamCircuitBreakerTests {

    @Mock
    private ChatMessageMapper chatMessageMapper;
    @Mock
    private ChatSessionMapper chatSessionMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private QaTraceLogMapper qaTraceLogMapper;
    @Mock
    private ChatModelGateway chatModelGateway;
    @Mock
    private RagRetrievalService ragRetrievalService;
    @Mock
    private RedissonClient redissonClient;
    @Mock
    private RAtomicLong messageSequenceCounter;
    private final AtomicLong redisSequenceState = new AtomicLong();
    @Mock
    private ExecutorService chatStreamExecutor;
    @Mock
    private ChatSessionSummaryService chatSessionSummaryService;
    @Mock
    private IntentNodeService intentNodeService;

    private MemoryProperties memoryProperties;
    private TraceProperties traceProperties;
    private AiProperties aiProperties;

    @InjectMocks
    private ChatMessageServiceImpl chatMessageService;

    @BeforeEach
    void setUp() {
        ApplicationContextHolder.clear();
        memoryProperties = new MemoryProperties();
        memoryProperties.setRecentWindowSize(12);
        traceProperties = new TraceProperties();
        traceProperties.setEnabled(true);
        traceProperties.setLogPayload(true);
        aiProperties = new AiProperties();
        aiProperties.getCircuitBreaker().setFirstTokenTimeoutMillis(2000);
        aiProperties.getCircuitBreaker().setStreamChunkIdleTimeoutMillis(2000);
        redisSequenceState.set(0L);
        lenient().when(chatMessageMapper.selectPage(any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(chatSessionMapper.selectOne(any())).thenReturn(session());
        lenient().when(userMapper.selectOne(any())).thenReturn(user());
        lenient().when(redissonClient.getAtomicLong(anyString())).thenReturn(messageSequenceCounter);
        lenient().when(messageSequenceCounter.get()).thenAnswer(invocation -> redisSequenceState.get());
        lenient().when(messageSequenceCounter.compareAndSet(anyLong(), anyLong())).thenAnswer(invocation ->
                redisSequenceState.compareAndSet(invocation.getArgument(0), invocation.getArgument(1)));
        lenient().when(messageSequenceCounter.addAndGet(2L)).thenAnswer(invocation ->
                redisSequenceState.addAndGet(invocation.getArgument(0)));
        chatMessageService = new ChatMessageServiceImpl(
                chatMessageMapper, chatSessionMapper, userMapper, qaTraceLogMapper,
                chatModelGateway, ragRetrievalService, memoryProperties,
                traceProperties, aiProperties, redissonClient, chatStreamExecutor,
                chatSessionSummaryService, intentNodeService);
        UserContext.set(LoginUser.builder().userId("20").build());
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
        ApplicationContextHolder.clear();
    }

    // ========== 普通流式熔断场景 ==========

    @Test
    void shouldEmitErrorWhenAllStreamingCandidatesFail() {
        when(chatModelGateway.classifyQuestionIntent("你好", List.of())).thenReturn("CHITCHAT");
        when(chatModelGateway.streamingCandidateIds()).thenReturn(List.of("candidate-a", "candidate-b"));
        when(chatModelGateway.tryAcquireStreamingCandidate("candidate-a")).thenReturn(true);
        when(chatModelGateway.tryAcquireStreamingCandidate("candidate-b")).thenReturn(true);
        when(chatModelGateway.streamChitchatByCandidate("candidate-a", "你好", List.of()))
                .thenReturn(Flux.error(new RuntimeException("model-a-down")));
        when(chatModelGateway.streamChitchatByCandidate("candidate-b", "你好", List.of()))
                .thenReturn(Flux.error(new RuntimeException("model-b-down")));

        List<ChatStreamEventResp> events = chatMessageService.buildChatStreamEvents(new ChatStreamReq()
                        .setSessionId(10L).setMessage("你好").setTraceId("trace-1"))
                .collectList().block(Duration.ofSeconds(3));

        assertThat(events).hasSize(1);
        assertThat(events.get(0).getEvent()).isEqualTo("error");
        verify(chatModelGateway).markStreamingCandidateFailure("candidate-a");
        verify(chatModelGateway).markStreamingCandidateFailure("candidate-b");
        // 用户消息在流式开始前已持久化，但助手消息不应被持久化
        verify(chatMessageMapper, times(1)).insert(any(ChatMessageDO.class));
    }

    @Test
    void shouldSkipCircuitOpenCandidateAndTryNext() {
        when(chatModelGateway.classifyQuestionIntent("你好", List.of())).thenReturn("CHITCHAT");
        when(chatModelGateway.streamingCandidateIds()).thenReturn(List.of("candidate-a", "candidate-b"));
        // candidate-a is circuit-open (tryAcquire returns false)
        when(chatModelGateway.tryAcquireStreamingCandidate("candidate-a")).thenReturn(false);
        when(chatModelGateway.tryAcquireStreamingCandidate("candidate-b")).thenReturn(true);
        when(chatModelGateway.streamChitchatByCandidate("candidate-b", "你好", List.of()))
                .thenReturn(Flux.just("你好！"));
        when(chatModelGateway.candidateInfo("candidate-b")).thenReturn(new ChatModelInfoRecord("bailian", "qwen-plus"));

        List<ChatStreamEventResp> events = chatMessageService.buildChatStreamEvents(new ChatStreamReq()
                        .setSessionId(10L).setMessage("你好").setTraceId("trace-2"))
                .collectList().block(Duration.ofSeconds(3));

        assertThat(events).extracting(ChatStreamEventResp::getEvent)
                .containsExactly("message_start", "delta", "message_end");
        verify(chatModelGateway, never()).streamChitchatByCandidate(eq("candidate-a"), any(), any());
        verify(chatModelGateway).markStreamingCandidateSuccess("candidate-b");
    }

    @Test
    void shouldEmitErrorWhenAllCandidatesCircuitOpen() {
        when(chatModelGateway.classifyQuestionIntent("你好", List.of())).thenReturn("CHITCHAT");
        when(chatModelGateway.streamingCandidateIds()).thenReturn(List.of("candidate-a", "candidate-b"));
        when(chatModelGateway.tryAcquireStreamingCandidate("candidate-a")).thenReturn(false);
        when(chatModelGateway.tryAcquireStreamingCandidate("candidate-b")).thenReturn(false);

        List<ChatStreamEventResp> events = chatMessageService.buildChatStreamEvents(new ChatStreamReq()
                        .setSessionId(10L).setMessage("你好").setTraceId("trace-3"))
                .collectList().block(Duration.ofSeconds(3));

        assertThat(events).hasSize(1);
        assertThat(events.get(0).getEvent()).isEqualTo("error");
        assertThat(events.get(0).getMessage()).contains("没有可用");
        verify(chatModelGateway, never()).streamChitchatByCandidate(any(), any(), any());
    }

    @Test
    void shouldEmitResetWhenFailoverAfterPartialDelta() {
        when(chatModelGateway.classifyQuestionIntent("你好", List.of())).thenReturn("CHITCHAT");
        when(chatModelGateway.streamingCandidateIds()).thenReturn(List.of("candidate-a", "candidate-b"));
        when(chatModelGateway.tryAcquireStreamingCandidate("candidate-a")).thenReturn(true);
        when(chatModelGateway.tryAcquireStreamingCandidate("candidate-b")).thenReturn(true);
        // candidate-a emits partial delta then fails
        when(chatModelGateway.streamChitchatByCandidate("candidate-a", "你好", List.of()))
                .thenReturn(Flux.concat(Flux.just("你"), Flux.error(new RuntimeException("mid-stream-error"))));
        when(chatModelGateway.streamChitchatByCandidate("candidate-b", "你好", List.of()))
                .thenReturn(Flux.just("你好！"));
        when(chatModelGateway.candidateInfo("candidate-b")).thenReturn(new ChatModelInfoRecord("bailian", "qwen-plus"));

        List<ChatStreamEventResp> events = chatMessageService.buildChatStreamEvents(new ChatStreamReq()
                        .setSessionId(10L).setMessage("你好").setTraceId("trace-4"))
                .collectList().block(Duration.ofSeconds(3));

        // candidate-a: message_start (first delta), delta ("你"), error → reset
        // candidate-b: message_start, delta ("你好！"), message_end
        assertThat(events).extracting(ChatStreamEventResp::getEvent)
                .containsSequence("message_start", "delta", "reset", "message_start", "delta", "message_end");
        verify(chatModelGateway).markStreamingCandidateFailure("candidate-a");
        verify(chatModelGateway).markStreamingCandidateSuccess("candidate-b");
    }

    @Test
    void shouldStreamNormallyWhenFirstCandidateSucceeds() {
        when(chatModelGateway.classifyQuestionIntent("你好", List.of())).thenReturn("CHITCHAT");
        when(chatModelGateway.streamingCandidateIds()).thenReturn(List.of("candidate-a", "candidate-b"));
        when(chatModelGateway.tryAcquireStreamingCandidate("candidate-a")).thenReturn(true);
        when(chatModelGateway.streamChitchatByCandidate("candidate-a", "你好", List.of()))
                .thenReturn(Flux.just("你", "好", "！"));
        when(chatModelGateway.candidateInfo("candidate-a")).thenReturn(new ChatModelInfoRecord("bailian", "qwen-plus"));

        List<ChatStreamEventResp> events = chatMessageService.buildChatStreamEvents(new ChatStreamReq()
                        .setSessionId(10L).setMessage("你好").setTraceId("trace-5"))
                .collectList().block(Duration.ofSeconds(3));

        assertThat(events).extracting(ChatStreamEventResp::getEvent)
                .containsExactly("message_start", "delta", "delta", "delta", "message_end");
        verify(chatModelGateway).markStreamingCandidateSuccess("candidate-a");
        // candidate-b should never be tried
        verify(chatModelGateway, never()).tryAcquireStreamingCandidate("candidate-b");
    }

    // ========== 深度思考流式熔断场景 ==========

    @Test
    void shouldEmitErrorWhenAllThinkingCandidatesFail() {
        when(chatModelGateway.classifyQuestionIntent("你好", List.of())).thenReturn("CHITCHAT");
        when(chatModelGateway.thinkingCandidateIds()).thenReturn(List.of("think-a", "think-b"));
        when(chatModelGateway.tryAcquireStreamingCandidate("think-a")).thenReturn(true);
        when(chatModelGateway.tryAcquireStreamingCandidate("think-b")).thenReturn(true);
        when(chatModelGateway.streamThinkingChitchatByCandidate("think-a", "你好", List.of()))
                .thenReturn(Flux.error(new RuntimeException("think-a-error")));
        when(chatModelGateway.streamThinkingChitchatByCandidate("think-b", "你好", List.of()))
                .thenReturn(Flux.error(new RuntimeException("think-b-error")));

        List<ChatStreamEventResp> events = chatMessageService.buildChatStreamEvents(new ChatStreamReq()
                        .setSessionId(10L).setMessage("你好")
                        .setDeepThinking(true).setTraceId("trace-6"))
                .collectList().block(Duration.ofSeconds(3));

        assertThat(events).hasSize(1);
        assertThat(events.get(0).getEvent()).isEqualTo("error");
        verify(chatModelGateway).markStreamingCandidateFailure("think-a");
        verify(chatModelGateway).markStreamingCandidateFailure("think-b");
    }

    @Test
    void shouldSkipCircuitOpenThinkingCandidateAndTryNext() {
        when(chatModelGateway.classifyQuestionIntent("你好", List.of())).thenReturn("CHITCHAT");
        when(chatModelGateway.thinkingCandidateIds()).thenReturn(List.of("think-a", "think-b"));
        when(chatModelGateway.tryAcquireStreamingCandidate("think-a")).thenReturn(false);
        when(chatModelGateway.tryAcquireStreamingCandidate("think-b")).thenReturn(true);
        when(chatModelGateway.streamThinkingChitchatByCandidate("think-b", "你好", List.of()))
                .thenReturn(Flux.just(
                        new StreamContentRecord("深度分析...", null),
                        new StreamContentRecord(null, "你好！")
                ));
        when(chatModelGateway.candidateInfo("think-b")).thenReturn(new ChatModelInfoRecord("bailian", "qwen-plus"));

        List<ChatStreamEventResp> events = chatMessageService.buildChatStreamEvents(new ChatStreamReq()
                        .setSessionId(10L).setMessage("你好")
                        .setDeepThinking(true).setTraceId("trace-7"))
                .collectList().block(Duration.ofSeconds(3));

        assertThat(events).extracting(ChatStreamEventResp::getEvent)
                .containsExactly("thinking_delta", "message_start", "delta", "message_end");
        assertThat(events.get(0).getType()).isEqualTo("think");
        verify(chatModelGateway, never()).streamThinkingChitchatByCandidate(eq("think-a"), any(), any());
    }

    @Test
    void shouldNotEmitResetWhenThinkingStreamFailsWithoutContentDelta() {
        // sawDelta 只追踪 content delta，thinking-only 失败不会触发 reset
        when(chatModelGateway.classifyQuestionIntent("你好", List.of())).thenReturn("CHITCHAT");
        when(chatModelGateway.thinkingCandidateIds()).thenReturn(List.of("think-a", "think-b"));
        when(chatModelGateway.tryAcquireStreamingCandidate("think-a")).thenReturn(true);
        when(chatModelGateway.tryAcquireStreamingCandidate("think-b")).thenReturn(true);
        // think-a emits thinking_delta then fails (no content delta)
        when(chatModelGateway.streamThinkingChitchatByCandidate("think-a", "你好", List.of()))
                .thenReturn(Flux.concat(
                        Flux.just(new StreamContentRecord("让我思考...", null)),
                        Flux.error(new RuntimeException("mid-thinking-error"))
                ));
        when(chatModelGateway.streamThinkingChitchatByCandidate("think-b", "你好", List.of()))
                .thenReturn(Flux.just(
                        new StreamContentRecord("重新思考...", null),
                        new StreamContentRecord(null, "你好！")
                ));
        when(chatModelGateway.candidateInfo("think-b")).thenReturn(new ChatModelInfoRecord("bailian", "qwen-plus"));

        List<ChatStreamEventResp> events = chatMessageService.buildChatStreamEvents(new ChatStreamReq()
                        .setSessionId(10L).setMessage("你好")
                        .setDeepThinking(true).setTraceId("trace-8"))
                .collectList().block(Duration.ofSeconds(3));

        // think-a fails without content delta → no reset, failover to think-b directly
        assertThat(events).extracting(ChatStreamEventResp::getEvent)
                .containsExactly("thinking_delta", "thinking_delta", "message_start", "delta", "message_end");
        verify(chatModelGateway).markStreamingCandidateFailure("think-a");
        verify(chatModelGateway).markStreamingCandidateSuccess("think-b");
    }

    @Test
    void shouldEmitResetWhenThinkingStreamFailsAfterContentDelta() {
        // 有 content delta 后失败，会触发 reset
        when(chatModelGateway.classifyQuestionIntent("你好", List.of())).thenReturn("CHITCHAT");
        when(chatModelGateway.thinkingCandidateIds()).thenReturn(List.of("think-a", "think-b"));
        when(chatModelGateway.tryAcquireStreamingCandidate("think-a")).thenReturn(true);
        when(chatModelGateway.tryAcquireStreamingCandidate("think-b")).thenReturn(true);
        // think-a emits thinking + partial content then fails
        when(chatModelGateway.streamThinkingChitchatByCandidate("think-a", "你好", List.of()))
                .thenReturn(Flux.concat(
                        Flux.just(new StreamContentRecord("分析...", null)),
                        Flux.just(new StreamContentRecord(null, "你")),
                        Flux.error(new RuntimeException("mid-content-error"))
                ));
        when(chatModelGateway.streamThinkingChitchatByCandidate("think-b", "你好", List.of()))
                .thenReturn(Flux.just(
                        new StreamContentRecord("再分析...", null),
                        new StreamContentRecord(null, "你好！")
                ));
        when(chatModelGateway.candidateInfo("think-b")).thenReturn(new ChatModelInfoRecord("bailian", "qwen-plus"));

        List<ChatStreamEventResp> events = chatMessageService.buildChatStreamEvents(new ChatStreamReq()
                        .setSessionId(10L).setMessage("你好")
                        .setDeepThinking(true).setTraceId("trace-9"))
                .collectList().block(Duration.ofSeconds(3));

        // think-a: thinking_delta, message_start (first content delta triggers it), delta, then error → reset
        // think-b: thinking_delta, message_start, delta, message_end
        assertThat(events).extracting(ChatStreamEventResp::getEvent)
                .containsSequence("thinking_delta", "message_start", "delta", "reset", "thinking_delta", "message_start", "delta", "message_end");
        verify(chatModelGateway).markStreamingCandidateFailure("think-a");
        verify(chatModelGateway).markStreamingCandidateSuccess("think-b");
    }

    @Test
    void shouldPersistThinkingContentAfterFailover() {
        when(chatModelGateway.classifyQuestionIntent("你好", List.of())).thenReturn("CHITCHAT");
        when(chatModelGateway.thinkingCandidateIds()).thenReturn(List.of("think-a", "think-b"));
        when(chatModelGateway.tryAcquireStreamingCandidate("think-a")).thenReturn(true);
        when(chatModelGateway.tryAcquireStreamingCandidate("think-b")).thenReturn(true);
        when(chatModelGateway.streamThinkingChitchatByCandidate("think-a", "你好", List.of()))
                .thenReturn(Flux.error(new RuntimeException("instant-fail")));
        when(chatModelGateway.streamThinkingChitchatByCandidate("think-b", "你好", List.of()))
                .thenReturn(Flux.just(
                        new StreamContentRecord("深度思考结果", null),
                        new StreamContentRecord(null, "你好！")
                ));
        when(chatModelGateway.candidateInfo("think-b")).thenReturn(new ChatModelInfoRecord("bailian", "qwen-plus"));

        chatMessageService.buildChatStreamEvents(new ChatStreamReq()
                        .setSessionId(10L).setMessage("你好")
                        .setDeepThinking(true).setTraceId("trace-10"))
                .collectList().block(Duration.ofSeconds(3));

        // 验证持久化的助手消息包含 think-b 的思考内容
        org.mockito.ArgumentCaptor<ChatMessageDO> captor = org.mockito.ArgumentCaptor.forClass(ChatMessageDO.class);
        verify(chatMessageMapper, times(2)).insert(captor.capture());
        ChatMessageDO assistantMessage = captor.getAllValues().get(1);
        assertThat(assistantMessage.getThinkingContent()).isEqualTo("深度思考结果");
        assertThat(assistantMessage.getContent()).isEqualTo("你好！");
    }

    @Test
    void shouldEmitErrorWhenNoStreamingCandidatesAvailable() {
        when(chatModelGateway.classifyQuestionIntent("你好", List.of())).thenReturn("CHITCHAT");
        when(chatModelGateway.streamingCandidateIds()).thenReturn(List.of());

        List<ChatStreamEventResp> events = chatMessageService.buildChatStreamEvents(new ChatStreamReq()
                        .setSessionId(10L).setMessage("你好").setTraceId("trace-11"))
                .collectList().block(Duration.ofSeconds(3));

        assertThat(events).hasSize(1);
        assertThat(events.get(0).getEvent()).isEqualTo("error");
        assertThat(events.get(0).getMessage()).contains("没有可用");
    }

    @Test
    void shouldEmitErrorWhenNoThinkingCandidatesAvailableForDeepThinking() {
        when(chatModelGateway.classifyQuestionIntent("你好", List.of())).thenReturn("CHITCHAT");
        when(chatModelGateway.thinkingCandidateIds()).thenReturn(List.of());

        List<ChatStreamEventResp> events = chatMessageService.buildChatStreamEvents(new ChatStreamReq()
                        .setSessionId(10L).setMessage("你好")
                        .setDeepThinking(true).setTraceId("trace-12"))
                .collectList().block(Duration.ofSeconds(3));

        assertThat(events).hasSize(1);
        assertThat(events.get(0).getEvent()).isEqualTo("error");
        assertThat(events.get(0).getMessage()).contains("深度思考模型");
    }

    private ChatSessionDO session() {
        ChatSessionDO sessionDO = new ChatSessionDO();
        sessionDO.setId(10L);
        sessionDO.setUserId(20L);
        sessionDO.setStatus(ChatSessionStatusEnum.ACTIVE.getCode());
        sessionDO.setDeleteFlag(DeleteFlagEnum.NORMAL.getCode());
        return sessionDO;
    }

    private UserDO user() {
        UserDO userDO = new UserDO();
        userDO.setId(20L);
        userDO.setRankLevel(10);
        userDO.setRoleCode("USER");
        userDO.setDeleteFlag(DeleteFlagEnum.NORMAL.getCode());
        return userDO;
    }
}
