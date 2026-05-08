package com.rag.cn.yuetaoragbackend.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rag.cn.yuetaoragbackend.config.record.ChatModelInfoRecord;
import com.rag.cn.yuetaoragbackend.config.enums.ChatSessionStatusEnum;
import com.rag.cn.yuetaoragbackend.config.enums.DeleteFlagEnum;
import com.rag.cn.yuetaoragbackend.config.properties.AiProperties;
import com.rag.cn.yuetaoragbackend.config.properties.MemoryProperties;
import com.rag.cn.yuetaoragbackend.config.properties.TraceProperties;
import com.rag.cn.yuetaoragbackend.framework.context.LoginUser;
import com.rag.cn.yuetaoragbackend.framework.context.UserContext;
import com.rag.cn.yuetaoragbackend.dao.entity.ChatMessageDO;
import com.rag.cn.yuetaoragbackend.dao.entity.ChatSessionDO;
import com.rag.cn.yuetaoragbackend.dao.entity.QaTraceLogDO;
import com.rag.cn.yuetaoragbackend.dao.entity.UserDO;
import com.rag.cn.yuetaoragbackend.dao.mapper.ChatMessageMapper;
import com.rag.cn.yuetaoragbackend.dao.mapper.ChatSessionMapper;
import com.rag.cn.yuetaoragbackend.dao.mapper.QaTraceLogMapper;
import com.rag.cn.yuetaoragbackend.dao.mapper.UserMapper;
import com.rag.cn.yuetaoragbackend.dto.req.ChatReq;
import com.rag.cn.yuetaoragbackend.dto.req.ChatStreamReq;
import com.rag.cn.yuetaoragbackend.dto.resp.ChatResp;
import com.rag.cn.yuetaoragbackend.dto.resp.ChatStreamEventResp;
import java.util.List;
import java.time.Duration;
import org.mockito.ArgumentCaptor;
import java.util.concurrent.ExecutorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

/**
 * @author zrq
 * 2026/04/29 16:10
 */
@ExtendWith(MockitoExtension.class)
class ChatMessageServiceImplTests {

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
    private ExecutorService chatStreamExecutor;

    private MemoryProperties memoryProperties;

    private TraceProperties traceProperties;

    private AiProperties aiProperties;

    @InjectMocks
    private ChatMessageServiceImpl chatMessageService;

    @BeforeEach
    void setUp() {
        memoryProperties = new MemoryProperties();
        memoryProperties.setRecentWindowSize(12);
        traceProperties = new TraceProperties();
        traceProperties.setEnabled(true);
        traceProperties.setLogPayload(true);
        aiProperties = new AiProperties();
        aiProperties.getCircuitBreaker().setFirstTokenTimeoutMillis(2000);
        aiProperties.getCircuitBreaker().setStreamChunkIdleTimeoutMillis(2000);
        lenient().when(chatMessageMapper.selectPage(any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        chatMessageService = new ChatMessageServiceImpl(
                chatMessageMapper,
                chatSessionMapper,
                userMapper,
                qaTraceLogMapper,
                chatModelGateway,
                ragRetrievalService,
                memoryProperties,
                traceProperties,
                aiProperties,
                chatStreamExecutor);
        UserContext.set(LoginUser.builder().userId("20").build());
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void shouldChatWithoutKnowledgeRetrievalForChitchatIntent() {
        when(chatSessionMapper.selectById(10L)).thenReturn(session());
        when(userMapper.selectById(20L)).thenReturn(user());
        when(chatModelGateway.classifyQuestionIntent("你好", List.of())).thenReturn("CHITCHAT");
        when(chatModelGateway.generateChitchatAnswer("你好", List.of()))
                .thenReturn("你好，请问有什么可以帮你？");
        when(chatModelGateway.currentModelInfo()).thenReturn(new ChatModelInfoRecord("bailian", "qwen-plus"));

        ChatResp response = chatMessageService.chat(new ChatReq()
                .setSessionId(10L)
                .setMessage("你好"));

        assertThat(response.getIntentType()).isEqualTo("CHITCHAT");
        assertThat(response.getKnowledgeHit()).isFalse();
        assertThat(response.getCitations()).isEmpty();
        assertThat(response.getAnswer()).contains("你好");
        verify(ragRetrievalService, never()).retrieve(any(), any());
        verify(ragRetrievalService, never()).rerank(any(String.class), any(List.class));
        verify(chatMessageMapper, times(2)).insert(any(ChatMessageDO.class));
    }

    @Test
    void shouldChatWithKnowledgeCitationsForKnowledgeIntent() {
        when(chatSessionMapper.selectById(10L)).thenReturn(session());
        when(userMapper.selectById(20L)).thenReturn(user());
        when(chatModelGateway.classifyQuestionIntent("商品退货规则是什么", List.of())).thenReturn("KB_QA");
        when(chatModelGateway.rewriteQuestion("商品退货规则是什么", List.of())).thenReturn("商品退货规则");
        List<RagRetrievalService.RetrievedChunk> recalledChunks = List.of(
                new RagRetrievalService.RetrievedChunk(101L, 201L, "商品退货规则", 3,
                        "商品支持7天无理由退货，特殊品类除外。", 0.82D, 0D, 0D));
        List<RagRetrievalService.RetrievedChunk> rerankedChunks = List.of(
                new RagRetrievalService.RetrievedChunk(101L, 201L, "商品退货规则", 3,
                        "商品支持7天无理由退货，特殊品类除外。", 0.82D, 0.50D, 0.71D));
        when(ragRetrievalService.retrieve(any(UserDO.class), any(String.class))).thenReturn(recalledChunks);
        when(ragRetrievalService.rerank(any(String.class), any(List.class))).thenReturn(rerankedChunks);
        when(chatModelGateway.generateAnswer(any(String.class), any(String.class), any(List.class), any(List.class)))
                .thenReturn("商品支持7天无理由退货[1]。");
        when(chatModelGateway.currentModelInfo()).thenReturn(new ChatModelInfoRecord("bailian", "qwen-plus"));

        ChatResp response = chatMessageService.chat(new ChatReq()
                .setSessionId(10L)
                .setMessage("商品退货规则是什么"));

        assertThat(response.getIntentType()).isEqualTo("KB_QA");
        assertThat(response.getKnowledgeHit()).isTrue();
        assertThat(response.getCitations()).hasSize(1);
        assertThat(response.getCitations().get(0).getReferenceLabel()).isEqualTo("商品退货规则（切片#3）");
        assertThat(response.getAnswer()).contains("[1]");
        verify(ragRetrievalService).retrieve(any(UserDO.class), any(String.class));
        verify(ragRetrievalService).rerank(any(String.class), any(List.class));
    }

    @Test
    void shouldRefuseAnswerWhenRerankResultsEmpty() {
        when(chatSessionMapper.selectById(10L)).thenReturn(session());
        when(userMapper.selectById(20L)).thenReturn(user());
        when(chatModelGateway.classifyQuestionIntent("报销流程是什么", List.of())).thenReturn("KB_QA");
        when(chatModelGateway.rewriteQuestion("报销流程是什么", List.of())).thenReturn("报销流程");
        when(ragRetrievalService.retrieve(any(UserDO.class), any(String.class))).thenReturn(List.of());
        when(ragRetrievalService.rerank(any(String.class), any(List.class))).thenReturn(List.of());
        when(chatModelGateway.currentModelInfo()).thenReturn(new ChatModelInfoRecord("bailian", "qwen-plus"));

        ChatResp response = chatMessageService.chat(new ChatReq()
                .setSessionId(10L)
                .setMessage("报销流程是什么"));

        assertThat(response.getIntentType()).isEqualTo("KB_QA");
        assertThat(response.getKnowledgeHit()).isFalse();
        assertThat(response.getAnswer()).isEqualTo("当前知识库中没有该方面的内容，暂时无法回答这个问题。");
        assertThat(response.getCitations()).isEmpty();
        verify(chatModelGateway, never()).generateAnswer(any(String.class), any(String.class), any(List.class), any(List.class));
    }

    @Test
    void shouldBuildStreamEventPayloadForDelta() {
        ChatStreamEventResp event = ChatStreamEventResp.delta("trace-1", 10L, "你好");

        assertThat(event.getEvent()).isEqualTo("delta");
        assertThat(event.getTraceId()).isEqualTo("trace-1");
        assertThat(event.getSessionId()).isEqualTo(10L);
        assertThat(event.getContent()).isEqualTo("你好");
    }

    @Test
    void shouldStreamRefusalAnswerWhenRerankResultsEmpty() {
        when(chatSessionMapper.selectById(10L)).thenReturn(session());
        when(userMapper.selectById(20L)).thenReturn(user());
        when(chatModelGateway.classifyQuestionIntent("报销流程是什么", List.of())).thenReturn("KB_QA");
        when(chatModelGateway.rewriteQuestion("报销流程是什么", List.of())).thenReturn("报销流程");
        when(ragRetrievalService.retrieve(any(UserDO.class), any(String.class))).thenReturn(List.of());
        when(ragRetrievalService.rerank(any(String.class), any(List.class))).thenReturn(List.of());
        when(chatModelGateway.streamingCandidateIds()).thenReturn(List.of("qwen-plus"));
        when(chatModelGateway.candidateInfo("qwen-plus")).thenReturn(new ChatModelInfoRecord("bailian", "qwen-plus"));

        List<ChatStreamEventResp> events = chatMessageService.buildChatStreamEvents(new ChatStreamReq()
                        .setSessionId(10L)
                        .setMessage("报销流程是什么"))
                .collectList()
                .block(Duration.ofSeconds(3));

        assertThat(events).extracting(ChatStreamEventResp::getEvent)
                .containsExactly("message_start", "delta", "message_end");
    }

    @Test
    void shouldEmitResetWhenFirstCandidateFailsAfterDelta() {
        when(chatSessionMapper.selectById(10L)).thenReturn(session());
        when(userMapper.selectById(20L)).thenReturn(user());
        when(chatModelGateway.classifyQuestionIntent("你好", List.of())).thenReturn("CHITCHAT");
        when(chatModelGateway.streamingCandidateIds()).thenReturn(List.of("candidate-a", "candidate-b"));
        when(chatModelGateway.tryAcquireStreamingCandidate("candidate-a")).thenReturn(true);
        when(chatModelGateway.tryAcquireStreamingCandidate("candidate-b")).thenReturn(true);
        when(chatModelGateway.streamChitchatByCandidate("candidate-a", "你好", List.of()))
                .thenReturn(Flux.concat(Flux.just("你"), Flux.error(new RuntimeException("stream-error"))));
        when(chatModelGateway.streamChitchatByCandidate("candidate-b", "你好", List.of()))
                .thenReturn(Flux.just("你好，请问有什么可以帮你？"));
        when(chatModelGateway.candidateInfo("candidate-b")).thenReturn(new ChatModelInfoRecord("bailian", "qwen-plus"));

        List<ChatStreamEventResp> events = chatMessageService.buildChatStreamEvents(new ChatStreamReq()
                        .setSessionId(10L)
                        .setMessage("你好")
                        .setTraceId("trace-1"))
                .collectList()
                .block(Duration.ofSeconds(3));

        assertThat(events).extracting(ChatStreamEventResp::getEvent)
                .containsSequence("delta", "reset", "message_start", "delta", "message_end");
    }

    @Test
    void shouldRecordPositiveLatenciesForStreamingTraceStages() {
        when(chatSessionMapper.selectById(10L)).thenReturn(session());
        when(userMapper.selectById(20L)).thenReturn(user());
        when(chatModelGateway.classifyQuestionIntent("你好", List.of())).thenReturn("CHITCHAT");
        when(chatModelGateway.streamingCandidateIds()).thenReturn(List.of("candidate-a"));
        when(chatModelGateway.tryAcquireStreamingCandidate("candidate-a")).thenReturn(true);
        when(chatModelGateway.streamChitchatByCandidate("candidate-a", "你好", List.of()))
                .thenReturn(Flux.just("你", "好"));
        when(chatModelGateway.candidateInfo("candidate-a")).thenReturn(new ChatModelInfoRecord("bailian", "qwen-plus"));

        List<ChatStreamEventResp> events = chatMessageService.buildChatStreamEvents(new ChatStreamReq()
                        .setSessionId(10L)
                        .setMessage("你好")
                        .setTraceId("trace-1"))
                .collectList()
                .block(Duration.ofSeconds(3));

        assertThat(events).extracting(ChatStreamEventResp::getEvent)
                .containsExactly("message_start", "delta", "delta", "message_end");

        ArgumentCaptor<QaTraceLogDO> captor = ArgumentCaptor.forClass(QaTraceLogDO.class);
        verify(qaTraceLogMapper, times(2)).insert(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(QaTraceLogDO::getLatencyMs)
                .allMatch(value -> value != null && value > 0);
    }

    @Test
    void shouldStreamThinkingDeltaEventsForDeepThinkingChitchat() {
        when(chatSessionMapper.selectById(10L)).thenReturn(session());
        when(userMapper.selectById(20L)).thenReturn(user());
        when(chatModelGateway.classifyQuestionIntent("你好", List.of())).thenReturn("CHITCHAT");
        when(chatModelGateway.thinkingCandidateIds()).thenReturn(List.of("qwen-thinking"));
        when(chatModelGateway.tryAcquireStreamingCandidate("qwen-thinking")).thenReturn(true);
        when(chatModelGateway.streamThinkingChitchatByCandidate("qwen-thinking", "你好", List.of()))
                .thenReturn(Flux.just(
                        new StreamContent("让我想想...", null),
                        new StreamContent(null, "你好！"),
                        new StreamContent(null, "有什么可以帮你？")
                ));
        when(chatModelGateway.candidateInfo("qwen-thinking")).thenReturn(new ChatModelInfoRecord("bailian", "qwen-plus"));

        List<ChatStreamEventResp> events = chatMessageService.buildChatStreamEvents(new ChatStreamReq()
                        .setSessionId(10L)
                        .setMessage("你好")
                        .setDeepThinking(true)
                        .setTraceId("trace-thinking"))
                .collectList()
                .block(Duration.ofSeconds(3));

        assertThat(events).extracting(ChatStreamEventResp::getEvent)
                .containsExactly("thinking_delta", "message_start", "delta", "delta", "message_end");
        assertThat(events.get(0).getType()).isEqualTo("think");
        assertThat(events.get(0).getContent()).isEqualTo("让我想想...");
        assertThat(events.get(1).getType()).isNull(); // message_start has no type
        assertThat(events.get(2).getType()).isEqualTo("response");
        assertThat(events.get(2).getContent()).isEqualTo("你好！");
    }

    @Test
    void shouldStreamThinkingWithKnowledgeCitations() {
        when(chatSessionMapper.selectById(10L)).thenReturn(session());
        when(userMapper.selectById(20L)).thenReturn(user());
        when(chatModelGateway.classifyQuestionIntent("退货规则", List.of())).thenReturn("KB_QA");
        when(chatModelGateway.rewriteQuestion("退货规则", List.of())).thenReturn("退货规则");
        List<RagRetrievalService.RetrievedChunk> recalledChunks = List.of(
                new RagRetrievalService.RetrievedChunk(101L, 201L, "退货政策", 1, "7天无理由退货", 0.82D, 0D, 0D));
        List<RagRetrievalService.RetrievedChunk> rerankedChunks = List.of(
                new RagRetrievalService.RetrievedChunk(101L, 201L, "退货政策", 1, "7天无理由退货", 0.82D, 0.50D, 0.71D));
        when(ragRetrievalService.retrieve(any(UserDO.class), any(String.class))).thenReturn(recalledChunks);
        when(ragRetrievalService.rerank(any(String.class), any(List.class))).thenReturn(rerankedChunks);
        when(chatModelGateway.thinkingCandidateIds()).thenReturn(List.of("qwen-thinking"));
        when(chatModelGateway.tryAcquireStreamingCandidate("qwen-thinking")).thenReturn(true);
        when(chatModelGateway.streamThinkingKnowledgeAnswerByCandidate(
                eq("qwen-thinking"), eq("退货规则"), eq("退货规则"), any(List.class), any(List.class)))
                .thenReturn(Flux.just(
                        new StreamContent("分析知识库...", null),
                        new StreamContent(null, "根据政策[1]，支持7天退货。")
                ));
        when(chatModelGateway.candidateInfo("qwen-thinking")).thenReturn(new ChatModelInfoRecord("bailian", "qwen-plus"));

        List<ChatStreamEventResp> events = chatMessageService.buildChatStreamEvents(new ChatStreamReq()
                        .setSessionId(10L)
                        .setMessage("退货规则")
                        .setDeepThinking(true)
                        .setTraceId("trace-kb"))
                .collectList()
                .block(Duration.ofSeconds(3));

        assertThat(events).extracting(ChatStreamEventResp::getEvent)
                .containsExactly("thinking_delta", "message_start", "delta", "citation", "message_end");
        assertThat(events.get(0).getContent()).isEqualTo("分析知识库...");
        assertThat(events.get(3).getCitations()).hasSize(1);
    }

    @Test
    void shouldPersistThinkingContentAndDuration() {
        when(chatSessionMapper.selectById(10L)).thenReturn(session());
        when(userMapper.selectById(20L)).thenReturn(user());
        when(chatModelGateway.classifyQuestionIntent("你好", List.of())).thenReturn("CHITCHAT");
        when(chatModelGateway.thinkingCandidateIds()).thenReturn(List.of("qwen-thinking"));
        when(chatModelGateway.tryAcquireStreamingCandidate("qwen-thinking")).thenReturn(true);
        when(chatModelGateway.streamThinkingChitchatByCandidate("qwen-thinking", "你好", List.of()))
                .thenReturn(Flux.just(
                        new StreamContent("思考中...", null),
                        new StreamContent(null, "你好！")
                ));
        when(chatModelGateway.candidateInfo("qwen-thinking")).thenReturn(new ChatModelInfoRecord("bailian", "qwen-plus"));

        chatMessageService.buildChatStreamEvents(new ChatStreamReq()
                        .setSessionId(10L)
                        .setMessage("你好")
                        .setDeepThinking(true))
                .collectList()
                .block(Duration.ofSeconds(3));

        ArgumentCaptor<ChatMessageDO> messageCaptor = ArgumentCaptor.forClass(ChatMessageDO.class);
        verify(chatMessageMapper, times(2)).insert(messageCaptor.capture());
        ChatMessageDO assistantMessage = messageCaptor.getAllValues().get(1);
        assertThat(assistantMessage.getThinkingContent()).isEqualTo("思考中...");
        assertThat(assistantMessage.getThinkingDurationMs()).isNotNull();
        assertThat(assistantMessage.getThinkingDurationMs()).isGreaterThan(0);
        assertThat(assistantMessage.getContent()).isEqualTo("你好！");
    }

    @Test
    void shouldBuildThinkingDeltaEventPayload() {
        ChatStreamEventResp event = ChatStreamEventResp.thinkingDelta("trace-1", 10L, "让我分析...");

        assertThat(event.getEvent()).isEqualTo("thinking_delta");
        assertThat(event.getTraceId()).isEqualTo("trace-1");
        assertThat(event.getSessionId()).isEqualTo(10L);
        assertThat(event.getContent()).isEqualTo("让我分析...");
        assertThat(event.getType()).isEqualTo("think");
    }

    @Test
    void shouldFallbackWhenNoThinkingCandidatesAvailable() {
        when(chatSessionMapper.selectById(10L)).thenReturn(session());
        when(userMapper.selectById(20L)).thenReturn(user());
        when(chatModelGateway.classifyQuestionIntent("你好", List.of())).thenReturn("CHITCHAT");
        when(chatModelGateway.thinkingCandidateIds()).thenReturn(List.of());

        List<ChatStreamEventResp> events = chatMessageService.buildChatStreamEvents(new ChatStreamReq()
                        .setSessionId(10L)
                        .setMessage("你好")
                        .setDeepThinking(true))
                .collectList()
                .block(Duration.ofSeconds(3));

        assertThat(events).extracting(ChatStreamEventResp::getEvent)
                .containsExactly("error");
        assertThat(events.get(0).getMessage()).contains("深度思考模型");
    }

    @Test
    void shouldFailoverToNextThinkingCandidateOnError() {
        when(chatSessionMapper.selectById(10L)).thenReturn(session());
        when(userMapper.selectById(20L)).thenReturn(user());
        when(chatModelGateway.classifyQuestionIntent("你好", List.of())).thenReturn("CHITCHAT");
        when(chatModelGateway.thinkingCandidateIds()).thenReturn(List.of("candidate-a", "candidate-b"));
        when(chatModelGateway.tryAcquireStreamingCandidate("candidate-a")).thenReturn(true);
        when(chatModelGateway.tryAcquireStreamingCandidate("candidate-b")).thenReturn(true);
        when(chatModelGateway.streamThinkingChitchatByCandidate("candidate-a", "你好", List.of()))
                .thenReturn(Flux.error(new RuntimeException("model-error")));
        when(chatModelGateway.streamThinkingChitchatByCandidate("candidate-b", "你好", List.of()))
                .thenReturn(Flux.just(
                        new StreamContent("思考...", null),
                        new StreamContent(null, "你好！")
                ));
        when(chatModelGateway.candidateInfo("candidate-b")).thenReturn(new ChatModelInfoRecord("bailian", "qwen-plus"));

        List<ChatStreamEventResp> events = chatMessageService.buildChatStreamEvents(new ChatStreamReq()
                        .setSessionId(10L)
                        .setMessage("你好")
                        .setDeepThinking(true))
                .collectList()
                .block(Duration.ofSeconds(3));

        assertThat(events).extracting(ChatStreamEventResp::getEvent)
                .containsExactly("thinking_delta", "message_start", "delta", "message_end");
    }

    @Test
    void shouldNotPersistUserMessageWhenStreamPreparationFails() {
        when(chatSessionMapper.selectById(10L)).thenReturn(session());
        when(userMapper.selectById(20L)).thenReturn(user());
        when(chatModelGateway.classifyQuestionIntent("你好", List.of()))
                .thenThrow(new RuntimeException("gateway down"));

        List<ChatStreamEventResp> events = chatMessageService.buildChatStreamEvents(new ChatStreamReq()
                        .setSessionId(10L)
                        .setMessage("你好"))
                .collectList()
                .block(Duration.ofSeconds(3));

        assertThat(events).extracting(ChatStreamEventResp::getEvent)
                .containsExactly("error");
        verify(chatMessageMapper, never()).insert(any(ChatMessageDO.class));
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
