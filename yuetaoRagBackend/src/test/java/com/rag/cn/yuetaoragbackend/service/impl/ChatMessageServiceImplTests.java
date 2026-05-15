package com.rag.cn.yuetaoragbackend.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rag.cn.yuetaoragbackend.config.record.ChatModelInfoRecord;
import com.rag.cn.yuetaoragbackend.config.record.StreamContentRecord;
import com.rag.cn.yuetaoragbackend.config.enums.ChatSessionStatusEnum;
import com.rag.cn.yuetaoragbackend.config.enums.DeleteFlagEnum;
import com.rag.cn.yuetaoragbackend.config.enums.IntentNodeKindEnum;
import com.rag.cn.yuetaoragbackend.config.properties.AiProperties;
import com.rag.cn.yuetaoragbackend.config.properties.MemoryProperties;
import com.rag.cn.yuetaoragbackend.config.properties.TraceProperties;
import com.rag.cn.yuetaoragbackend.framework.context.ApplicationContextHolder;
import com.rag.cn.yuetaoragbackend.framework.context.LoginUser;
import com.rag.cn.yuetaoragbackend.framework.context.UserContext;
import com.rag.cn.yuetaoragbackend.dao.projection.RetrievedChunk;
import com.rag.cn.yuetaoragbackend.dao.entity.ChatMessageDO;
import com.rag.cn.yuetaoragbackend.dao.entity.ChatSessionDO;
import com.rag.cn.yuetaoragbackend.dao.entity.QaTraceLogDO;
import com.rag.cn.yuetaoragbackend.dao.entity.UserDO;
import com.rag.cn.yuetaoragbackend.dao.mapper.ChatMessageMapper;
import com.rag.cn.yuetaoragbackend.dao.mapper.ChatSessionMapper;
import com.rag.cn.yuetaoragbackend.dao.mapper.QaTraceLogMapper;
import com.rag.cn.yuetaoragbackend.dao.mapper.UserMapper;
import com.rag.cn.yuetaoragbackend.service.ChatSessionSummaryService;
import com.rag.cn.yuetaoragbackend.service.IntentNodeService;
import com.rag.cn.yuetaoragbackend.dto.req.ChatReq;
import com.rag.cn.yuetaoragbackend.dto.req.ChatStreamReq;
import com.rag.cn.yuetaoragbackend.dto.req.CreateChatMessageReq;
import com.rag.cn.yuetaoragbackend.dto.req.StopChatStreamReq;
import com.rag.cn.yuetaoragbackend.dto.resp.ChatResp;
import com.rag.cn.yuetaoragbackend.dto.resp.ChatStreamEventResp;
import com.rag.cn.yuetaoragbackend.dto.resp.IntentNodeTreeResp;
import com.rag.cn.yuetaoragbackend.framework.exception.ClientException;
import java.util.List;
import java.util.Map;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.mockito.ArgumentCaptor;
import java.util.concurrent.ExecutorService;
import java.lang.reflect.Method;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RedissonClient;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import org.slf4j.LoggerFactory;

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

    private ObjectMapper objectMapper;

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
        objectMapper = new ObjectMapper();
        redisSequenceState.set(0L);
        lenient().when(chatMessageMapper.selectPage(any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(redissonClient.getAtomicLong(anyString()))
                .thenReturn(messageSequenceCounter);
        lenient().when(messageSequenceCounter.get()).thenAnswer(invocation -> redisSequenceState.get());
        lenient().when(messageSequenceCounter.compareAndSet(anyLong(), anyLong())).thenAnswer(invocation ->
                redisSequenceState.compareAndSet(invocation.getArgument(0), invocation.getArgument(1)));
        lenient().when(messageSequenceCounter.addAndGet(2L)).thenAnswer(invocation ->
                redisSequenceState.addAndGet(invocation.getArgument(0)));
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
                objectMapper,
                redissonClient,
                chatStreamExecutor,
                chatSessionSummaryService,
                intentNodeService);
        lenient().when(chatSessionMapper.selectOne(any())).thenReturn(session());
        lenient().when(userMapper.selectOne(any())).thenReturn(user());
        UserContext.set(LoginUser.builder().userId("20").build());
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
        ApplicationContextHolder.clear();
    }

    @Test
    void shouldChatWithoutKnowledgeRetrievalForChitchatIntent() {
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
        ArgumentCaptor<ChatMessageDO> messageCaptor = ArgumentCaptor.forClass(ChatMessageDO.class);
        verify(chatMessageMapper, times(2)).insert(messageCaptor.capture());
        assertThat(messageCaptor.getAllValues())
                .extracting(ChatMessageDO::getSequenceNo)
                .containsExactly(1, 2);
    }

    @Test
    void shouldChatNormallyWhenTraceWriteFails() {
        when(qaTraceLogMapper.insert(any(QaTraceLogDO.class)))
                .thenThrow(new RuntimeException("trace-write-failed"));
        when(chatModelGateway.classifyQuestionIntent("你好", List.of())).thenReturn("CHITCHAT");
        when(chatModelGateway.generateChitchatAnswer("你好", List.of()))
                .thenReturn("你好，请问有什么可以帮你？");
        when(chatModelGateway.currentModelInfo()).thenReturn(new ChatModelInfoRecord("bailian", "qwen-plus"));

        ChatResp response = chatMessageService.chat(new ChatReq()
                .setSessionId(10L)
                .setMessage("你好"));

        assertThat(response.getIntentType()).isEqualTo("CHITCHAT");
        assertThat(response.getAnswer()).contains("你好");
        verify(chatMessageMapper, times(2)).insert(any(ChatMessageDO.class));
    }

    @Test
    void shouldContinueAllocatingAfterLatestDbSequenceWhenRedisCounterBehind() {
        when(chatMessageMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<ChatMessageDO> page = invocation.getArgument(0);
            if (page.getSize() == 1) {
                page.setRecords(List.of(new ChatMessageDO().setSequenceNo(8)));
            } else {
                page.setRecords(List.of());
            }
            return page;
        });
        when(chatModelGateway.classifyQuestionIntent("你好", List.of())).thenReturn("CHITCHAT");
        when(chatModelGateway.generateChitchatAnswer("你好", List.of()))
                .thenReturn("你好，请问有什么可以帮你？");
        when(chatModelGateway.currentModelInfo()).thenReturn(new ChatModelInfoRecord("bailian", "qwen-plus"));

        chatMessageService.chat(new ChatReq()
                .setSessionId(10L)
                .setMessage("你好"));

        ArgumentCaptor<ChatMessageDO> messageCaptor = ArgumentCaptor.forClass(ChatMessageDO.class);
        verify(chatMessageMapper, times(2)).insert(messageCaptor.capture());
        assertThat(messageCaptor.getAllValues())
                .extracting(ChatMessageDO::getSequenceNo)
                .containsExactly(9, 10);
        assertThat(redisSequenceState.get()).isEqualTo(10L);
    }

    @Test
    void shouldNotWrapWholeChatMethodInTransactionalAnnotation() throws NoSuchMethodException {
        Method chatMethod = ChatMessageServiceImpl.class.getMethod("chat", ChatReq.class);

        assertThat(AnnotatedElementUtils.hasAnnotation(chatMethod, Transactional.class)).isFalse();
    }

    @Test
    void shouldChatWithKnowledgeCitationsForKnowledgeIntent() {
        when(chatModelGateway.classifyQuestionIntent("商品退货规则是什么", List.of())).thenReturn("KB_QA");
        when(chatModelGateway.rewriteQuestion("商品退货规则是什么", List.of())).thenReturn("商品退货规则");
        List<RetrievedChunk> recalledChunks = List.of(
                new RetrievedChunk(101L, 301L, 201L, "商品退货规则", 3,
                        "商品支持7天无理由退货，特殊品类除外。", 0.82D, 0D, 0D));
        List<RetrievedChunk> rerankedChunks = List.of(
                new RetrievedChunk(101L, 301L, 201L, "商品退货规则", 3,
                        "商品支持7天无理由退货，特殊品类除外。", 0.82D, 0.50D, 0.71D));
        when(ragRetrievalService.retrieve(any(UserDO.class), any(String.class))).thenReturn(recalledChunks);
        when(ragRetrievalService.rerank(any(String.class), any(List.class))).thenReturn(rerankedChunks);
        when(chatModelGateway.generateAnswer(any(String.class), any(String.class), any(List.class), any(List.class), any()))
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
        verify(chatModelGateway, never()).generateAnswer(any(String.class), any(String.class), any(List.class), any(List.class), any());
    }

    @Test
    void shouldSelectKbLeafAndScopeRetrievalToTopKnowledgeBases() {
        when(chatModelGateway.rewriteQuestion("开题报告怎么写", List.of())).thenReturn("开题报告怎么写");
        when(intentNodeService.getTree()).thenReturn(List.of(
                node("group", IntentNodeKindEnum.KB.getCode(), null, null,
                        List.of(node("group-it", IntentNodeKindEnum.KB.getCode(), 401L, "IT规则", List.of()))),
                node("system-hello", IntentNodeKindEnum.SYSTEM.getCode(), null, "系统规则", List.of())
        ));
        when(chatModelGateway.scoreLeafIntent(eq("开题报告怎么写"), any(IntentNodeTreeResp.class))).thenAnswer(invocation -> {
            IntentNodeTreeResp node = invocation.getArgument(1);
            return switch (node.getIntentCode()) {
                case "group-it" -> 0.92D;
                case "system-hello" -> 0.88D;
                default -> 0D;
            };
        });
        List<RetrievedChunk> recalledChunks = List.of(
                new RetrievedChunk(101L, 401L, 201L, "开题指南", 1, "先确定研究问题。", 0.82D, 0D, 0D));
        List<RetrievedChunk> rerankedChunks = List.of(
                new RetrievedChunk(101L, 401L, 201L, "开题指南", 1, "先确定研究问题。", 0.82D, 0.61D, 0.77D));
        when(ragRetrievalService.retrieveByKnowledgeBaseIds(any(UserDO.class), eq("开题报告怎么写"), eq(List.of(401L))))
                .thenReturn(recalledChunks);
        when(ragRetrievalService.rerank(anyString(), any(List.class))).thenReturn(rerankedChunks);
        when(chatModelGateway.generateAnswer(any(String.class), any(String.class), any(List.class), eq(rerankedChunks), any()))
                .thenReturn("先确定研究问题[1]。");
        when(chatModelGateway.currentModelInfo()).thenReturn(new ChatModelInfoRecord("bailian", "qwen-plus"));

        ChatResp response = chatMessageService.chat(new ChatReq()
                .setSessionId(10L)
                .setMessage("开题报告怎么写"));

        assertThat(response.getKnowledgeHit()).isTrue();
        assertThat(response.getAnswer()).contains("研究问题");
        verify(ragRetrievalService).retrieveByKnowledgeBaseIds(any(UserDO.class), eq("开题报告怎么写"), eq(List.of(401L)));
    }

    @Test
    void shouldIgnoreSystemLeafWhenKbLeafAlsoMatches() {
        when(chatModelGateway.rewriteQuestion("你能做什么以及开题报告怎么写", List.of())).thenReturn("你能做什么以及开题报告怎么写");
        when(intentNodeService.getTree()).thenReturn(List.of(
                node("biz-kaiti", IntentNodeKindEnum.KB.getCode(), 501L, "KB规则", List.of()),
                node("system-capability", IntentNodeKindEnum.SYSTEM.getCode(), null, "SYSTEM规则", List.of())
        ));
        when(chatModelGateway.scoreLeafIntent(eq("你能做什么以及开题报告怎么写"), any(IntentNodeTreeResp.class))).thenAnswer(invocation -> {
            IntentNodeTreeResp node = invocation.getArgument(1);
            return switch (node.getIntentCode()) {
                case "biz-kaiti" -> 0.81D;
                case "system-capability" -> 0.99D;
                default -> 0D;
            };
        });
        when(ragRetrievalService.retrieveByKnowledgeBaseIds(any(UserDO.class), anyString(), eq(List.of(501L))))
                .thenReturn(List.of());
        when(ragRetrievalService.retrieve(any(UserDO.class), anyString())).thenReturn(List.of());
        when(ragRetrievalService.rerank(anyString(), any(List.class))).thenReturn(List.of());
        when(chatModelGateway.currentModelInfo()).thenReturn(new ChatModelInfoRecord("bailian", "qwen-plus"));

        ChatResp response = chatMessageService.chat(new ChatReq()
                .setSessionId(10L)
                .setMessage("你能做什么以及开题报告怎么写"));

        assertThat(response.getAnswer()).isEqualTo("当前知识库中没有该方面的内容，暂时无法回答这个问题。");
        verify(ragRetrievalService).retrieveByKnowledgeBaseIds(any(UserDO.class), anyString(), eq(List.of(501L)));
        verify(chatModelGateway, never()).generateChitchatAnswer(anyString(), any(List.class));
    }

    @Test
    void shouldUseSystemLeafWhenNoKbLeafMatches() {
        when(chatModelGateway.rewriteQuestion("你能做什么", List.of())).thenReturn("你能做什么");
        when(intentNodeService.getTree()).thenReturn(List.of(
                node("system-capability", IntentNodeKindEnum.SYSTEM.getCode(), null, "请用系统模板回答", List.of())
        ));
        when(chatModelGateway.scoreLeafIntent(eq("你能做什么"), any(IntentNodeTreeResp.class))).thenReturn(0.95D);
        when(chatModelGateway.generateChitchatAnswer("你能做什么", List.of()))
                .thenReturn("我是内部知识助手，可以回答企业知识问题。");
        when(chatModelGateway.currentModelInfo()).thenReturn(new ChatModelInfoRecord("bailian", "qwen-plus"));

        ChatResp response = chatMessageService.chat(new ChatReq()
                .setSessionId(10L)
                .setMessage("你能做什么"));

        assertThat(response.getKnowledgeHit()).isFalse();
        assertThat(response.getAnswer()).contains("内部知识助手");
        verify(ragRetrievalService, never()).retrieve(any(UserDO.class), anyString());
        verify(ragRetrievalService, never()).retrieveByKnowledgeBaseIds(any(UserDO.class), anyString(), any(List.class));
        verify(chatModelGateway).generateChitchatAnswer("你能做什么", List.of());
    }

    @Test
    void shouldCreateMessageForCurrentUser() {
        chatMessageService.createChatMessage(new CreateChatMessageReq()
                .setSessionId(10L)
                .setRole("USER")
                .setContent("你好")
                .setSequenceNo(1));

        ArgumentCaptor<ChatMessageDO> messageCaptor = ArgumentCaptor.forClass(ChatMessageDO.class);
        verify(chatMessageMapper).insert(messageCaptor.capture());
        assertThat(messageCaptor.getValue().getUserId()).isEqualTo(20L);
        assertThat(messageCaptor.getValue().getSessionId()).isEqualTo(10L);
        assertThat(messageCaptor.getValue().getContent()).isEqualTo("你好");
    }

    @Test
    void shouldRejectCreateMessageWhenSessionBelongsToAnotherUser() {
        ChatSessionDO sessionDO = new ChatSessionDO();
        sessionDO.setId(10L);
        sessionDO.setUserId(99L);
        sessionDO.setStatus(ChatSessionStatusEnum.ACTIVE.getCode());
        sessionDO.setDeleteFlag(DeleteFlagEnum.NORMAL.getCode());
        when(chatSessionMapper.selectOne(any())).thenReturn(sessionDO);

        assertThatThrownBy(() -> chatMessageService.createChatMessage(new CreateChatMessageReq()
                .setSessionId(10L)
                .setRole("USER")
                .setContent("你好")
                .setSequenceNo(1)))
                .isInstanceOf(ClientException.class)
                .hasMessage("无权访问该会话");
        verify(chatMessageMapper, never()).insert(any(ChatMessageDO.class));
    }

    @Test
    void shouldReturnNullWhenMessageDeleted() {
        assertThat(chatMessageService.getChatMessage(99L)).isNull();
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
    void shouldEmitErrorWithoutFailoverWhenAssistantPersistenceFailsAfterStreamingContent() {
        when(chatMessageMapper.insert(any(ChatMessageDO.class))).thenAnswer(invocation -> {
            ChatMessageDO message = invocation.getArgument(0);
            if ("ASSISTANT".equals(message.getRole())) {
                throw new RuntimeException("db-write-failed");
            }
            return 1;
        });
        when(chatModelGateway.classifyQuestionIntent("你好", List.of())).thenReturn("CHITCHAT");
        when(chatModelGateway.streamingCandidateIds()).thenReturn(List.of("candidate-a", "candidate-b"));
        when(chatModelGateway.tryAcquireStreamingCandidate("candidate-a")).thenReturn(true);
        when(chatModelGateway.streamChitchatByCandidate("candidate-a", "你好", List.of()))
                .thenReturn(Flux.just("你好！"));
        when(chatModelGateway.candidateInfo("candidate-a")).thenReturn(new ChatModelInfoRecord("bailian", "qwen-plus"));

        List<ChatStreamEventResp> events = chatMessageService.buildChatStreamEvents(new ChatStreamReq()
                        .setSessionId(10L)
                        .setMessage("你好")
                        .setTraceId("trace-persist-fail"))
                .collectList()
                .block(Duration.ofSeconds(3));

        assertThat(events).extracting(ChatStreamEventResp::getEvent)
                .containsExactly("message_start", "delta", "error");
        assertThat(events.get(2).getMessage()).contains("保存失败");
        verify(chatModelGateway).markStreamingCandidateSuccess("candidate-a");
        verify(chatModelGateway, never()).markStreamingCandidateFailure("candidate-a");
        verify(chatModelGateway, never()).tryAcquireStreamingCandidate("candidate-b");
    }

    @Test
    void shouldRecordPositiveLatenciesForStreamingTraceStages() {
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
        verify(qaTraceLogMapper, org.mockito.Mockito.atLeast(4)).insert(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(QaTraceLogDO::getLatencyMs)
                .allMatch(value -> value != null && value > 0);
    }

    @Test
    void shouldStreamNormallyWhenSuccessTraceWriteFails() {
        when(qaTraceLogMapper.insert(any(QaTraceLogDO.class))).thenAnswer(invocation -> {
            QaTraceLogDO traceLogDO = invocation.getArgument(0);
            if ("STREAM_CANDIDATE".equals(traceLogDO.getStage())
                    && "SUCCESS".equals(traceLogDO.getStatus())) {
                throw new RuntimeException("trace-write-failed");
            }
            return 1;
        });
        when(chatModelGateway.classifyQuestionIntent("你好", List.of())).thenReturn("CHITCHAT");
        when(chatModelGateway.streamingCandidateIds()).thenReturn(List.of("candidate-a"));
        when(chatModelGateway.tryAcquireStreamingCandidate("candidate-a")).thenReturn(true);
        when(chatModelGateway.streamChitchatByCandidate("candidate-a", "你好", List.of()))
                .thenReturn(Flux.just("你", "好"));
        when(chatModelGateway.candidateInfo("candidate-a")).thenReturn(new ChatModelInfoRecord("bailian", "qwen-plus"));

        List<ChatStreamEventResp> events = chatMessageService.buildChatStreamEvents(new ChatStreamReq()
                        .setSessionId(10L)
                        .setMessage("你好")
                        .setTraceId("trace-write-fail"))
                .collectList()
                .block(Duration.ofSeconds(3));

        assertThat(events).extracting(ChatStreamEventResp::getEvent)
                .containsExactly("message_start", "delta", "delta", "message_end");
    }

    @Test
    void shouldPersistStreamingCompletionOffModelSourceScheduler() {
        AtomicReference<String> assistantInsertThread = new AtomicReference<>();
        AtomicReference<String> sessionUpdateThread = new AtomicReference<>();
        AtomicReference<String> successTraceThread = new AtomicReference<>();
        when(chatMessageMapper.insert(any(ChatMessageDO.class))).thenAnswer(invocation -> {
            ChatMessageDO message = invocation.getArgument(0);
            if ("ASSISTANT".equals(message.getRole())) {
                assistantInsertThread.set(Thread.currentThread().getName());
            }
            return 1;
        });
        when(chatSessionMapper.updateById(any(ChatSessionDO.class))).thenAnswer(invocation -> {
            sessionUpdateThread.set(Thread.currentThread().getName());
            return 1;
        });
        when(qaTraceLogMapper.insert(any(QaTraceLogDO.class))).thenAnswer(invocation -> {
            QaTraceLogDO traceLogDO = invocation.getArgument(0);
            if ("STREAM_CANDIDATE".equals(traceLogDO.getStage())
                    && "SUCCESS".equals(traceLogDO.getStatus())) {
                successTraceThread.set(Thread.currentThread().getName());
            }
            return 1;
        });
        when(chatModelGateway.classifyQuestionIntent("你好", List.of())).thenReturn("CHITCHAT");
        when(chatModelGateway.streamingCandidateIds()).thenReturn(List.of("candidate-a"));
        when(chatModelGateway.tryAcquireStreamingCandidate("candidate-a")).thenReturn(true);
        when(chatModelGateway.candidateInfo("candidate-a")).thenReturn(new ChatModelInfoRecord("bailian", "qwen-plus"));

        Scheduler sourceScheduler = Schedulers.newSingle("stream-source");
        try {
            when(chatModelGateway.streamChitchatByCandidate("candidate-a", "你好", List.of()))
                    .thenReturn(Flux.just("你", "好").publishOn(sourceScheduler));

            List<ChatStreamEventResp> events = chatMessageService.buildChatStreamEvents(new ChatStreamReq()
                            .setSessionId(10L)
                            .setMessage("你好")
                            .setTraceId("trace-offload"))
                    .collectList()
                    .block(Duration.ofSeconds(3));

            assertThat(events).extracting(ChatStreamEventResp::getEvent)
                    .containsExactly("message_start", "delta", "delta", "message_end");
            assertThat(assistantInsertThread.get()).startsWith("boundedElastic-");
            assertThat(sessionUpdateThread.get()).startsWith("boundedElastic-");
            assertThat(successTraceThread.get()).startsWith("boundedElastic-");
        } finally {
            sourceScheduler.dispose();
        }
    }

    @Test
    void shouldStreamThinkingDeltaEventsForDeepThinkingChitchat() {
        when(chatModelGateway.classifyQuestionIntent("你好", List.of())).thenReturn("CHITCHAT");
        when(chatModelGateway.thinkingCandidateIds()).thenReturn(List.of("qwen-thinking"));
        when(chatModelGateway.tryAcquireStreamingCandidate("qwen-thinking")).thenReturn(true);
        when(chatModelGateway.streamThinkingChitchatByCandidate("qwen-thinking", "你好", List.of()))
                .thenReturn(Flux.just(
                        new StreamContentRecord("让我想想...", null),
                        new StreamContentRecord(null, "你好！"),
                        new StreamContentRecord(null, "有什么可以帮你？")
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
    void shouldStopActiveStreamByTraceIdAndDisposeSubscription() throws InterruptedException {
        AtomicBoolean cancelled = new AtomicBoolean(false);
        CountDownLatch started = new CountDownLatch(1);
        org.mockito.Mockito.doAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run();
            return null;
        }).when(chatStreamExecutor).execute(any(Runnable.class));
        when(chatModelGateway.classifyQuestionIntent("你好", List.of())).thenReturn("CHITCHAT");
        when(chatModelGateway.streamingCandidateIds()).thenReturn(List.of("candidate-a"));
        when(chatModelGateway.tryAcquireStreamingCandidate("candidate-a")).thenReturn(true);
        when(chatModelGateway.streamChitchatByCandidate("candidate-a", "你好", List.of()))
                .thenReturn(Flux.create((FluxSink<String> sink) -> {
                    started.countDown();
                    sink.onCancel(() -> cancelled.set(true));
                }));

        chatMessageService.chatStream(new ChatStreamReq()
                .setSessionId(10L)
                .setMessage("你好")
                .setTraceId("trace-stop"));

        assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();

        boolean stopped = chatMessageService.stopChatStream(new StopChatStreamReq()
                .setSessionId(10L)
                .setTraceId("trace-stop"));

        assertThat(stopped).isTrue();
        assertThat(cancelled.get()).isTrue();
    }

    @Test
    void shouldLogWhenUserStopsActiveStream() throws InterruptedException {
        Logger logger = (Logger) LoggerFactory.getLogger(ChatMessageServiceImpl.class);
        ListAppender<ILoggingEvent> logAppender = new ListAppender<>();
        logAppender.start();
        logger.addAppender(logAppender);
        try {
            AtomicBoolean cancelled = new AtomicBoolean(false);
            CountDownLatch started = new CountDownLatch(1);
            org.mockito.Mockito.doAnswer(invocation -> {
                Runnable task = invocation.getArgument(0);
                task.run();
                return null;
            }).when(chatStreamExecutor).execute(any(Runnable.class));
            when(chatModelGateway.classifyQuestionIntent("你好", List.of())).thenReturn("CHITCHAT");
            when(chatModelGateway.streamingCandidateIds()).thenReturn(List.of("candidate-a"));
            when(chatModelGateway.tryAcquireStreamingCandidate("candidate-a")).thenReturn(true);
            when(chatModelGateway.streamChitchatByCandidate("candidate-a", "你好", List.of()))
                    .thenReturn(Flux.create((FluxSink<String> sink) -> {
                        started.countDown();
                        sink.onCancel(() -> cancelled.set(true));
                    }));

            chatMessageService.chatStream(new ChatStreamReq()
                    .setSessionId(10L)
                    .setMessage("你好")
                    .setTraceId("trace-stop-log"));

            assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();

            boolean stopped = chatMessageService.stopChatStream(new StopChatStreamReq()
                    .setSessionId(10L)
                    .setTraceId("trace-stop-log"));

            assertThat(stopped).isTrue();
            assertThat(cancelled.get()).isTrue();
            assertThat(logAppender.list)
                    .extracting(ILoggingEvent::getFormattedMessage)
                    .anyMatch(message -> message.contains("用户主动停止流式对话")
                            && message.contains("traceId=trace-stop-log")
                            && message.contains("sessionId=10")
                            && message.contains("userId=20"));
        } finally {
            logger.detachAppender(logAppender);
            logAppender.stop();
        }
    }

    @Test
    void shouldStreamThinkingWithKnowledgeCitations() {
        when(chatModelGateway.classifyQuestionIntent("退货规则", List.of())).thenReturn("KB_QA");
        when(chatModelGateway.rewriteQuestion("退货规则", List.of())).thenReturn("退货规则");
        List<RetrievedChunk> recalledChunks = List.of(
                new RetrievedChunk(101L, 301L, 201L, "退货政策", 1, "7天无理由退货", 0.82D, 0D, 0D));
        List<RetrievedChunk> rerankedChunks = List.of(
                new RetrievedChunk(101L, 301L, 201L, "退货政策", 1, "7天无理由退货", 0.82D, 0.50D, 0.71D));
        when(ragRetrievalService.retrieve(any(UserDO.class), any(String.class))).thenReturn(recalledChunks);
        when(ragRetrievalService.rerank(any(String.class), any(List.class))).thenReturn(rerankedChunks);
        when(chatModelGateway.thinkingCandidateIds()).thenReturn(List.of("qwen-thinking"));
        when(chatModelGateway.tryAcquireStreamingCandidate("qwen-thinking")).thenReturn(true);
        when(chatModelGateway.streamThinkingKnowledgeAnswerByCandidate(
                eq("qwen-thinking"), eq("退货规则"), eq("退货规则"), any(List.class), any(List.class), any()))
                .thenReturn(Flux.just(
                        new StreamContentRecord("分析知识库...", null),
                        new StreamContentRecord(null, "根据政策[1]，支持7天退货。")
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
    void shouldWriteStructuredTracePayloadsForScopedKnowledgeRoute() throws Exception {
        IntentNodeTreeResp leaf = new IntentNodeTreeResp()
                .setIntentCode("biz-kaiti")
                .setName("开题报告")
                .setKind(IntentNodeKindEnum.KB.getCode())
                .setEnabled(1)
                .setKbId(301L)
                .setPromptSnippet("请优先参考开题报告知识库");
        List<RetrievedChunk> recalledChunks = List.of(
                new RetrievedChunk(101L, 301L, 201L, "开题报告模板", 1, "模板内容", 0.92D, 0D, 0D));
        List<RetrievedChunk> rerankedChunks = List.of(
                new RetrievedChunk(101L, 301L, 201L, "开题报告模板", 1, "模板内容", 0.92D, 0.88D, 0.90D));
        when(intentNodeService.getTree()).thenReturn(List.of(leaf));
        when(chatModelGateway.scoreLeafIntent("开题报告怎么写", leaf)).thenReturn(0.91D);
        when(chatModelGateway.rewriteQuestion("开题报告怎么写", List.of())).thenReturn("开题报告怎么写");
        when(ragRetrievalService.retrieveByKnowledgeBaseIds(any(UserDO.class), eq("开题报告怎么写"), eq(List.of(301L))))
                .thenReturn(recalledChunks);
        when(ragRetrievalService.rerank("开题报告怎么写", recalledChunks)).thenReturn(rerankedChunks);
        when(chatModelGateway.generateAnswer(
                eq("开题报告怎么写"), eq("开题报告怎么写"), any(List.class), eq(rerankedChunks), eq("请优先参考开题报告知识库")))
                .thenReturn("可以按研究背景、研究目标、研究方法来组织开题报告。");
        when(chatModelGateway.currentModelInfo()).thenReturn(new ChatModelInfoRecord("bailian", "qwen-plus"));

        ChatResp response = chatMessageService.chat(new ChatReq()
                .setSessionId(10L)
                .setMessage("开题报告怎么写")
                .setTraceId("trace-detail-sync"));

        assertThat(response.getKnowledgeHit()).isTrue();

        ArgumentCaptor<QaTraceLogDO> captor = ArgumentCaptor.forClass(QaTraceLogDO.class);
        verify(qaTraceLogMapper, org.mockito.Mockito.atLeast(5)).insert(captor.capture());
        Map<String, String> payloadByStage = captor.getAllValues().stream()
                .collect(java.util.stream.Collectors.toMap(
                        QaTraceLogDO::getStage,
                        QaTraceLogDO::getPayloadRef,
                        (left, right) -> right,
                        java.util.LinkedHashMap::new));

        Map<String, Object> intentPayload = objectMapper.readValue(payloadByStage.get("INTENT"), new TypeReference<>() {
        });
        assertThat(intentPayload)
                .containsEntry("resolvedIntentType", "KB_QA")
                .containsEntry("fallbackModel", false)
                .containsEntry("routeSource", "LEAF_SCORE");
        assertThat((List<Integer>) intentPayload.get("knowledgeBaseIds")).containsExactly(301);

        Map<String, Object> intentScorePayload = objectMapper.readValue(payloadByStage.get("INTENT_SCORE"), new TypeReference<>() {
        });
        assertThat(intentScorePayload)
                .containsEntry("leafCount", 1)
                .containsEntry("matchedCount", 1);
        assertThat((List<Integer>) intentScorePayload.get("routedKnowledgeBaseIds")).containsExactly(301);
        assertThat((List<Map<String, Object>>) intentScorePayload.get("matchedLeaves"))
                .extracting(item -> item.get("intentCode"))
                .containsExactly("biz-kaiti");
    }

    @Test
    void shouldWriteStructuredTracePayloadsForStreamingKnowledgeRoute() throws Exception {
        when(chatModelGateway.classifyQuestionIntent("退货规则", List.of())).thenReturn("KB_QA");
        when(chatModelGateway.rewriteQuestion("退货规则", List.of())).thenReturn("退货规则");
        List<RetrievedChunk> recalledChunks = List.of(
                new RetrievedChunk(101L, 301L, 201L, "退货政策", 1, "7天无理由退货", 0.82D, 0D, 0D));
        List<RetrievedChunk> rerankedChunks = List.of(
                new RetrievedChunk(101L, 301L, 201L, "退货政策", 1, "7天无理由退货", 0.82D, 0.50D, 0.71D));
        when(ragRetrievalService.retrieve(any(UserDO.class), any(String.class))).thenReturn(recalledChunks);
        when(ragRetrievalService.rerank(any(String.class), any(List.class))).thenReturn(rerankedChunks);
        when(chatModelGateway.thinkingCandidateIds()).thenReturn(List.of("qwen-thinking"));
        when(chatModelGateway.tryAcquireStreamingCandidate("qwen-thinking")).thenReturn(true);
        when(chatModelGateway.streamThinkingKnowledgeAnswerByCandidate(
                eq("qwen-thinking"), eq("退货规则"), eq("退货规则"), any(List.class), any(List.class), any()))
                .thenReturn(Flux.just(
                        new StreamContentRecord("分析知识库...", null),
                        new StreamContentRecord(null, "根据政策[1]，支持7天退货。")
                ));
        when(chatModelGateway.candidateInfo("qwen-thinking")).thenReturn(new ChatModelInfoRecord("bailian", "qwen-plus"));

        List<ChatStreamEventResp> events = chatMessageService.buildChatStreamEvents(new ChatStreamReq()
                        .setSessionId(10L)
                        .setMessage("退货规则")
                        .setDeepThinking(true)
                        .setTraceId("trace-detail-stream"))
                .collectList()
                .block(Duration.ofSeconds(3));

        assertThat(events).extracting(ChatStreamEventResp::getEvent)
                .containsExactly("thinking_delta", "message_start", "delta", "citation", "message_end");

        ArgumentCaptor<QaTraceLogDO> captor = ArgumentCaptor.forClass(QaTraceLogDO.class);
        verify(qaTraceLogMapper, org.mockito.Mockito.atLeast(5)).insert(captor.capture());
        Map<String, String> payloadByStage = captor.getAllValues().stream()
                .collect(java.util.stream.Collectors.toMap(
                        QaTraceLogDO::getStage,
                        QaTraceLogDO::getPayloadRef,
                        (left, right) -> right,
                        java.util.LinkedHashMap::new));

        Map<String, Object> rewritePayload = objectMapper.readValue(payloadByStage.get("REWRITE"), new TypeReference<>() {
        });
        assertThat(rewritePayload)
                .containsEntry("originalQuestion", "退货规则")
                .containsEntry("rewrittenQuestion", "退货规则");

        Map<String, Object> intentPayload = objectMapper.readValue(payloadByStage.get("INTENT"), new TypeReference<>() {
        });
        assertThat(intentPayload)
                .containsEntry("resolvedIntentType", "KB_QA")
                .containsEntry("fallbackModel", true)
                .containsEntry("routeSource", "MODEL_CLASSIFIER");

        Map<String, Object> retrievePayload = objectMapper.readValue(payloadByStage.get("RETRIEVE"), new TypeReference<>() {
        });
        assertThat(retrievePayload)
                .containsEntry("scope", "GLOBAL")
                .containsEntry("candidateCount", 1);

        Map<String, Object> rerankPayload = objectMapper.readValue(payloadByStage.get("RERANK"), new TypeReference<>() {
        });
        assertThat(rerankPayload)
                .containsEntry("rerankCount", 1);

        Map<String, Object> candidatePayload = objectMapper.readValue(payloadByStage.get("STREAM_CANDIDATE"), new TypeReference<>() {
        });
        assertThat(candidatePayload)
                .containsEntry("candidateId", "qwen-thinking")
                .containsEntry("attemptNo", 1)
                .containsEntry("deepThinking", true);

        Map<String, Object> generatePayload = objectMapper.readValue(payloadByStage.get("GENERATE"), new TypeReference<>() {
        });
        assertThat(generatePayload)
                .containsEntry("answerSource", "STREAM_MODEL")
                .containsEntry("citationCount", 1)
                .containsEntry("deepThinking", true);
    }

    @Test
    void shouldPersistThinkingContentAndDuration() {
        when(chatModelGateway.classifyQuestionIntent("你好", List.of())).thenReturn("CHITCHAT");
        when(chatModelGateway.thinkingCandidateIds()).thenReturn(List.of("qwen-thinking"));
        when(chatModelGateway.tryAcquireStreamingCandidate("qwen-thinking")).thenReturn(true);
        when(chatModelGateway.streamThinkingChitchatByCandidate("qwen-thinking", "你好", List.of()))
                .thenReturn(Flux.just(
                        new StreamContentRecord("思考中...", null),
                        new StreamContentRecord(null, "你好！")
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
        assertThat(messageCaptor.getAllValues())
                .extracting(ChatMessageDO::getSequenceNo)
                .containsExactly(1, 2);
        ChatMessageDO assistantMessage = messageCaptor.getAllValues().get(1);
        assertThat(assistantMessage.getThinkingContent()).isEqualTo("思考中...");
        assertThat(assistantMessage.getThinkingDurationMs()).isNotNull();
        assertThat(assistantMessage.getThinkingDurationMs()).isGreaterThan(0);
        assertThat(assistantMessage.getContent()).isEqualTo("你好！");
    }

    @Test
    void shouldEmitErrorWithoutFailoverWhenThinkingAssistantPersistenceFailsAfterStreamingContent() {
        when(chatMessageMapper.insert(any(ChatMessageDO.class))).thenAnswer(invocation -> {
            ChatMessageDO message = invocation.getArgument(0);
            if ("ASSISTANT".equals(message.getRole())) {
                throw new RuntimeException("db-write-failed");
            }
            return 1;
        });
        when(chatModelGateway.classifyQuestionIntent("你好", List.of())).thenReturn("CHITCHAT");
        when(chatModelGateway.thinkingCandidateIds()).thenReturn(List.of("think-a", "think-b"));
        when(chatModelGateway.tryAcquireStreamingCandidate("think-a")).thenReturn(true);
        when(chatModelGateway.streamThinkingChitchatByCandidate("think-a", "你好", List.of()))
                .thenReturn(Flux.just(
                        new StreamContentRecord("思考中...", null),
                        new StreamContentRecord(null, "你好！")
                ));
        when(chatModelGateway.candidateInfo("think-a")).thenReturn(new ChatModelInfoRecord("bailian", "qwen-plus"));

        List<ChatStreamEventResp> events = chatMessageService.buildChatStreamEvents(new ChatStreamReq()
                        .setSessionId(10L)
                        .setMessage("你好")
                        .setDeepThinking(true)
                        .setTraceId("trace-thinking-persist-fail"))
                .collectList()
                .block(Duration.ofSeconds(3));

        assertThat(events).extracting(ChatStreamEventResp::getEvent)
                .containsExactly("thinking_delta", "message_start", "delta", "error");
        assertThat(events.get(3).getMessage()).contains("保存失败");
        verify(chatModelGateway).markStreamingCandidateSuccess("think-a");
        verify(chatModelGateway, never()).markStreamingCandidateFailure("think-a");
        verify(chatModelGateway, never()).tryAcquireStreamingCandidate("think-b");
    }

    @Test
    void shouldPersistThinkingStreamingCompletionOffModelSourceScheduler() {
        AtomicReference<String> assistantInsertThread = new AtomicReference<>();
        AtomicReference<String> sessionUpdateThread = new AtomicReference<>();
        AtomicReference<String> successTraceThread = new AtomicReference<>();
        when(chatMessageMapper.insert(any(ChatMessageDO.class))).thenAnswer(invocation -> {
            ChatMessageDO message = invocation.getArgument(0);
            if ("ASSISTANT".equals(message.getRole())) {
                assistantInsertThread.set(Thread.currentThread().getName());
            }
            return 1;
        });
        when(chatSessionMapper.updateById(any(ChatSessionDO.class))).thenAnswer(invocation -> {
            sessionUpdateThread.set(Thread.currentThread().getName());
            return 1;
        });
        when(qaTraceLogMapper.insert(any(QaTraceLogDO.class))).thenAnswer(invocation -> {
            QaTraceLogDO traceLogDO = invocation.getArgument(0);
            if ("STREAM_CANDIDATE".equals(traceLogDO.getStage())
                    && "SUCCESS".equals(traceLogDO.getStatus())) {
                successTraceThread.set(Thread.currentThread().getName());
            }
            return 1;
        });
        when(chatModelGateway.classifyQuestionIntent("你好", List.of())).thenReturn("CHITCHAT");
        when(chatModelGateway.thinkingCandidateIds()).thenReturn(List.of("qwen-thinking"));
        when(chatModelGateway.tryAcquireStreamingCandidate("qwen-thinking")).thenReturn(true);
        when(chatModelGateway.candidateInfo("qwen-thinking")).thenReturn(new ChatModelInfoRecord("bailian", "qwen-plus"));

        Scheduler sourceScheduler = Schedulers.newSingle("think-source");
        try {
            when(chatModelGateway.streamThinkingChitchatByCandidate("qwen-thinking", "你好", List.of()))
                    .thenReturn(Flux.just(
                                    new StreamContentRecord("思考中...", null),
                                    new StreamContentRecord(null, "你好！"))
                            .publishOn(sourceScheduler));

            List<ChatStreamEventResp> events = chatMessageService.buildChatStreamEvents(new ChatStreamReq()
                            .setSessionId(10L)
                            .setMessage("你好")
                            .setDeepThinking(true)
                            .setTraceId("trace-thinking-offload"))
                    .collectList()
                    .block(Duration.ofSeconds(3));

            assertThat(events).extracting(ChatStreamEventResp::getEvent)
                    .containsExactly("thinking_delta", "message_start", "delta", "message_end");
            assertThat(assistantInsertThread.get()).startsWith("boundedElastic-");
            assertThat(sessionUpdateThread.get()).startsWith("boundedElastic-");
            assertThat(successTraceThread.get()).startsWith("boundedElastic-");
        } finally {
            sourceScheduler.dispose();
        }
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
        when(chatModelGateway.classifyQuestionIntent("你好", List.of())).thenReturn("CHITCHAT");
        when(chatModelGateway.thinkingCandidateIds()).thenReturn(List.of("candidate-a", "candidate-b"));
        when(chatModelGateway.tryAcquireStreamingCandidate("candidate-a")).thenReturn(true);
        when(chatModelGateway.tryAcquireStreamingCandidate("candidate-b")).thenReturn(true);
        when(chatModelGateway.streamThinkingChitchatByCandidate("candidate-a", "你好", List.of()))
                .thenReturn(Flux.error(new RuntimeException("model-error")));
        when(chatModelGateway.streamThinkingChitchatByCandidate("candidate-b", "你好", List.of()))
                .thenReturn(Flux.just(
                        new StreamContentRecord("思考...", null),
                        new StreamContentRecord(null, "你好！")
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

    private IntentNodeTreeResp node(String intentCode, Integer kind, Long kbId, String promptSnippet,
                                    List<IntentNodeTreeResp> children) {
        return new IntentNodeTreeResp()
                .setIntentCode(intentCode)
                .setName(intentCode)
                .setEnabled(1)
                .setKind(kind)
                .setKbId(kbId)
                .setPromptSnippet(promptSnippet)
                .setChildren(children);
    }
}
