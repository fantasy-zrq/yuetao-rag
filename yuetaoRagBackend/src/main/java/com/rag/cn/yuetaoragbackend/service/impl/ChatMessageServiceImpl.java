package com.rag.cn.yuetaoragbackend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.rag.cn.yuetaoragbackend.config.enums.ChatMessageContentTypeEnum;
import com.rag.cn.yuetaoragbackend.config.enums.ChatSessionStatusEnum;
import com.rag.cn.yuetaoragbackend.config.enums.DeleteFlagEnum;
import com.rag.cn.yuetaoragbackend.config.enums.IntentNodeKindEnum;
import com.rag.cn.yuetaoragbackend.config.properties.AiProperties;
import com.rag.cn.yuetaoragbackend.config.properties.MemoryProperties;
import com.rag.cn.yuetaoragbackend.config.properties.TraceProperties;
import com.rag.cn.yuetaoragbackend.config.record.ChatModelInfoRecord;
import com.rag.cn.yuetaoragbackend.dao.entity.*;
import com.rag.cn.yuetaoragbackend.dao.mapper.ChatMessageMapper;
import com.rag.cn.yuetaoragbackend.dao.mapper.ChatSessionMapper;
import com.rag.cn.yuetaoragbackend.dao.mapper.QaTraceLogMapper;
import com.rag.cn.yuetaoragbackend.dao.mapper.UserMapper;
import com.rag.cn.yuetaoragbackend.dao.projection.RetrievedChunk;
import com.rag.cn.yuetaoragbackend.dto.req.ChatReq;
import com.rag.cn.yuetaoragbackend.dto.req.ChatStreamReq;
import com.rag.cn.yuetaoragbackend.dto.req.CreateChatMessageReq;
import com.rag.cn.yuetaoragbackend.dto.resp.*;
import com.rag.cn.yuetaoragbackend.framework.context.UserContext;
import com.rag.cn.yuetaoragbackend.framework.errorcode.BaseErrorCode;
import com.rag.cn.yuetaoragbackend.framework.exception.ClientException;
import com.rag.cn.yuetaoragbackend.service.ChatMessageService;
import com.rag.cn.yuetaoragbackend.service.ChatSessionSummaryService;
import com.rag.cn.yuetaoragbackend.service.IntentNodeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

/**
 * @author zrq
 * 2026/04/22 15:10
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChatMessageServiceImpl extends ServiceImpl<ChatMessageMapper, ChatMessageDO>
        implements ChatMessageService {

    private static final long CHAT_STREAM_TIMEOUT_MILLIS = 180_000L;

    private final ChatMessageMapper chatMessageMapper;
    private final ChatSessionMapper chatSessionMapper;
    private final UserMapper userMapper;
    private final QaTraceLogMapper qaTraceLogMapper;
    private final ChatModelGateway chatModelGateway;
    private final RagRetrievalService ragRetrievalService;
    private final MemoryProperties memoryProperties;
    private final TraceProperties traceProperties;
    private final AiProperties aiProperties;
    private final RedissonClient redissonClient;
    private final ExecutorService chatStreamExecutor;
    private final ChatSessionSummaryService chatSessionSummaryService;
    private final IntentNodeService intentNodeService;

    private static final double INTENT_SCORE_THRESHOLD = 0.5D;
    private static final int MAX_ROUTE_KB_COUNT = 3;
    private static final String STATIC_REFUSAL_ANSWER = "当前知识库中没有该方面的内容，暂时无法回答这个问题。";

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChatResp chat(ChatReq requestParam) {
        validateChatRequest(requestParam);
        Long userId = currentUserId();

        requireSession(requestParam.getSessionId(), userId);
        UserDO userDO = requireUser(userId);

        String traceId = StringUtils.hasText(requestParam.getTraceId()) ? requestParam.getTraceId() : UUID.randomUUID().toString();
        long chatStart = System.nanoTime();

        log.info("[CHAT] 收到对话请求: sessionId={}, traceId={}, messageLen={}",
                requestParam.getSessionId(), traceId, requestParam.getMessage().length());

        List<String> historyTexts = loadConversationContext(requestParam.getSessionId());

        RouteDecision routeDecision = resolveRouteDecision(requestParam.getMessage(), historyTexts,
                traceId, requestParam.getSessionId(), userId);
        String rewrittenQuery = routeDecision.requiresRewrite()
                ? rewriteQuestion(requestParam.getMessage(), historyTexts, traceId, requestParam.getSessionId(), userId)
                : requestParam.getMessage();
        SyncChatOutcome outcome = executeSyncRoute(routeDecision, requestParam.getMessage(), rewrittenQuery,
                historyTexts, userDO, traceId, requestParam.getSessionId(), userId);

        SequenceReservation sequenceReservation = reserveMessageSequences(requestParam.getSessionId());
        ChatModelInfoRecord modelInfo = chatModelGateway.currentModelInfo();
        ChatMessageDO userMessage = new ChatMessageDO()
                .setSessionId(requestParam.getSessionId())
                .setUserId(userId)
                .setRole("USER")
                .setContent(requestParam.getMessage())
                .setContentType(ChatMessageContentTypeEnum.TEXT.getCode())
                .setSequenceNo(sequenceReservation.userSequenceNo())
                .setTraceId(traceId);
        chatMessageMapper.insert(userMessage);

        ChatMessageDO assistantMessage = new ChatMessageDO()
                .setSessionId(requestParam.getSessionId())
                .setUserId(userId)
                .setRole("ASSISTANT")
                .setContent(outcome.answer())
                .setContentType(ChatMessageContentTypeEnum.TEXT.getCode())
                .setSequenceNo(sequenceReservation.assistantSequenceNo())
                .setTraceId(traceId)
                .setModelProvider(modelInfo.provider())
                .setModelName(modelInfo.modelName());
        chatMessageMapper.insert(assistantMessage);

        ChatSessionDO updateSession = new ChatSessionDO();
        updateSession.setId(requestParam.getSessionId());
        updateSession.setLastActiveAt(new Date());
        chatSessionMapper.updateById(updateSession);

        chatSessionSummaryService.maybeSummarize(requestParam.getSessionId(), sequenceReservation.assistantSequenceNo());

        log.info("[CHAT] 对话完成: sessionId={}, knowledgeHit={}, citationCount={}, elapsed={}ms",
                requestParam.getSessionId(), outcome.knowledgeHit(), outcome.citations().size(), elapsedMillis(chatStart));

        return new ChatResp()
                .setSessionId(requestParam.getSessionId())
                .setUserMessageId(userMessage.getId())
                .setAssistantMessageId(assistantMessage.getId())
                .setTraceId(traceId)
                .setIntentType(routeDecision.intentType())
                .setKnowledgeHit(outcome.knowledgeHit())
                .setRewrittenQuery(rewrittenQuery)
                .setAnswer(outcome.answer())
                .setCitations(outcome.citations());
    }

    @Override
    public SseEmitter chatStream(ChatStreamReq requestParam) {
        SseEmitter emitter = new SseEmitter(CHAT_STREAM_TIMEOUT_MILLIS);
        AtomicReference<Disposable> subscriptionRef = new AtomicReference<>();
        AtomicBoolean closed = new AtomicBoolean(false);
        Runnable cancelSubscription = () -> {
            closed.set(true);
            Disposable subscription = subscriptionRef.get();
            if (subscription != null && !subscription.isDisposed()) {
                subscription.dispose();
            }
        };
        emitter.onCompletion(cancelSubscription);
        emitter.onTimeout(() -> {
            cancelSubscription.run();
            completeEmitter(emitter);
        });
        emitter.onError(throwable -> cancelSubscription.run());
        chatStreamExecutor.execute(() -> sendChatStreamEvents(requestParam, emitter, subscriptionRef, closed));
        return emitter;
    }

    private void sendChatStreamEvents(ChatStreamReq requestParam, SseEmitter emitter,
                                      AtomicReference<Disposable> subscriptionRef,
                                      AtomicBoolean closed) {
        try {
            Disposable subscription = buildChatStreamEvents(requestParam)
                    .subscribe(
                            each -> {
                                if (closed.get()) {
                                    return;
                                }
                                try {
                                    sendStreamEvent(emitter, each);
                                } catch (Exception ex) {
                                    log.warn("SSE onNext 发送异常，终止流", ex);
                                    closed.set(true);
                                }
                            },
                            ex -> completeEmitterWithError(emitter, requestParam, ex, closed),
                            () -> {
                                if (closed.compareAndSet(false, true)) {
                                    completeEmitter(emitter);
                                }
                            });
            subscriptionRef.set(subscription);
            if (closed.get() && !subscription.isDisposed()) {
                subscription.dispose();
            }
        } catch (Exception ex) {
            completeEmitterWithError(emitter, requestParam, ex, closed);
        }
    }

    Flux<ChatStreamEventResp> buildChatStreamEvents(ChatStreamReq requestParam) {
        try {
            validateStreamRequest(requestParam);
            Long userId = currentUserId();
            ChatSessionDO sessionDO = requireSession(requestParam.getSessionId(), userId);
            UserDO userDO = requireUser(userId);
            String traceId = StringUtils.hasText(requestParam.getTraceId()) ? requestParam.getTraceId() : UUID.randomUUID().toString();
            long chatStart = System.nanoTime();

            log.info("[CHAT] 收到流式对话请求: sessionId={}, traceId={}, messageLen={}, deepThinking={}",
                    requestParam.getSessionId(), traceId, requestParam.getMessage().length(),
                    Boolean.TRUE.equals(requestParam.getDeepThinking()));

            List<String> historyTexts = loadConversationContext(requestParam.getSessionId());
            boolean deepThinking = Boolean.TRUE.equals(requestParam.getDeepThinking());
            RouteDecision routeDecision = resolveRouteDecision(requestParam.getMessage(), historyTexts,
                    traceId, requestParam.getSessionId(), userId);
            String rewrittenQuery = routeDecision.requiresRewrite()
                    ? rewriteQuestion(requestParam.getMessage(), historyTexts, traceId, requestParam.getSessionId(), userId)
                    : requestParam.getMessage();
            RetrievalPlan retrievalPlan = executeRetrievalPlan(routeDecision, rewrittenQuery, userDO, traceId,
                    requestParam.getSessionId(), userId);

            SequenceReservation sequenceReservation = reserveMessageSequences(requestParam.getSessionId());
            persistUserMessage(requestParam, userId, traceId, sequenceReservation.userSequenceNo());
            return buildStreamRouteEvents(routeDecision, retrievalPlan, requestParam, sessionDO, historyTexts,
                    traceId, userId, deepThinking, sequenceReservation.assistantSequenceNo());
        } catch (ClientException ex) {
            return Flux.just(ChatStreamEventResp.error(requestParam == null ? null : requestParam.getTraceId(),
                    requestParam == null ? null : requestParam.getSessionId(),
                    BaseErrorCode.CLIENT_ERROR.code(),
                    ex.getErrorMessage()));
        } catch (RuntimeException ex) {
            return Flux.just(ChatStreamEventResp.error(requestParam == null ? null : requestParam.getTraceId(),
                    requestParam == null ? null : requestParam.getSessionId(),
                    BaseErrorCode.SERVICE_ERROR.code(),
                    ex.getMessage()));
        }
    }

    private void validateChatRequest(ChatReq requestParam) {
        if (requestParam == null || requestParam.getSessionId() == null
                || !StringUtils.hasText(requestParam.getMessage())) {
            throw new ClientException("会话ID和消息内容不能为空");
        }
    }

    private void sendStreamEvent(SseEmitter emitter, ChatStreamEventResp event) {
        try {
            emitter.send(SseEmitter.event()
                    .id(event.getTraceId())
                    .name(event.getEvent())
                    .data(event));
        } catch (Exception ex) {
            log.warn("发送 SSE 事件失败: event={}", event.getEvent(), ex);
        }
    }

    private void sendUnexpectedStreamError(SseEmitter emitter, ChatStreamReq requestParam, Throwable throwable) {
        try {
            emitter.send(SseEmitter.event()
                    .name("error")
                    .data(ChatStreamEventResp.error(
                            requestParam == null ? null : requestParam.getTraceId(),
                            requestParam == null ? null : requestParam.getSessionId(),
                            BaseErrorCode.SERVICE_ERROR.code(),
                            throwable.getMessage())));
        } catch (Exception ex) {
            log.warn("发送 SSE 错误事件失败", ex);
        }
    }

    private void completeEmitter(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (Exception ex) {
            log.debug("SSE emitter 已关闭，忽略 complete 调用", ex);
        }
    }

    private void completeEmitterWithError(SseEmitter emitter, ChatStreamReq requestParam, Throwable throwable,
                                          AtomicBoolean closed) {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        sendUnexpectedStreamError(emitter, requestParam, throwable);
        try {
            emitter.completeWithError(throwable);
        } catch (Exception ex) {
            log.debug("SSE emitter 已关闭，忽略 completeWithError 调用", ex);
        }
    }

    @Override
    public ChatMessageCreateResp createChatMessage(CreateChatMessageReq requestParam) {
        Long userId = currentUserId();
        requireSessionForCreate(requestParam.getSessionId(), userId);
        requireUser(userId);
        ChatMessageDO messageDO = new ChatMessageDO()
                .setSessionId(requestParam.getSessionId())
                .setUserId(userId)
                .setRole(requestParam.getRole())
                .setContent(requestParam.getContent())
                .setContentType(requestParam.getContentType() == null || requestParam.getContentType().isBlank()
                        ? ChatMessageContentTypeEnum.TEXT.getCode() : requestParam.getContentType())
                .setSequenceNo(requestParam.getSequenceNo())
                .setTraceId(requestParam.getTraceId())
                .setModelProvider(requestParam.getModelProvider())
                .setModelName(requestParam.getModelName());
        chatMessageMapper.insert(messageDO);
        ChatMessageCreateResp response = new ChatMessageCreateResp();
        BeanUtils.copyProperties(messageDO, response);
        return response;
    }

    @Override
    public List<ChatMessageListResp> listBySessionId(Long sessionId) {
        return chatMessageMapper.selectList(Wrappers.<ChatMessageDO>lambdaQuery()
                        .eq(ChatMessageDO::getDeleteFlag, DeleteFlagEnum.NORMAL.getCode())
                        .eq(ChatMessageDO::getSessionId, sessionId)
                        .orderByAsc(ChatMessageDO::getSequenceNo))
                .stream()
                .map(each -> {
                    ChatMessageListResp response = new ChatMessageListResp();
                    BeanUtils.copyProperties(each, response);
                    return response;
                })
                .toList();
    }

    @Override
    public ChatMessageDetailResp getChatMessage(Long id) {
        ChatMessageDO messageDO = chatMessageMapper.selectOne(Wrappers.<ChatMessageDO>lambdaQuery()
                .eq(ChatMessageDO::getUserId, currentUserId())
                .eq(ChatMessageDO::getId, id)
                .eq(ChatMessageDO::getDeleteFlag, DeleteFlagEnum.NORMAL.getCode()));
        if (messageDO == null) {
            return null;
        }
        ChatMessageDetailResp response = new ChatMessageDetailResp();
        BeanUtils.copyProperties(messageDO, response);
        return response;
    }

    private ChatSessionDO requireSession(Long sessionId, Long userId) {
        ChatSessionDO sessionDO = chatSessionMapper.selectOne(Wrappers.<ChatSessionDO>lambdaQuery()
                .eq(ChatSessionDO::getId, sessionId)
                .eq(ChatSessionDO::getDeleteFlag, DeleteFlagEnum.NORMAL.getCode()));
        if (sessionDO == null) {
            throw new ClientException("会话不存在");
        }
        if (!userId.equals(sessionDO.getUserId())) {
            throw new ClientException("无权访问该会话");
        }
        if (!ChatSessionStatusEnum.ACTIVE.getCode().equals(sessionDO.getStatus())) {
            throw new ClientException("当前会话不可继续提问");
        }
        return sessionDO;
    }

    private ChatSessionDO requireSessionForCreate(Long sessionId, Long userId) {
        ChatSessionDO sessionDO = chatSessionMapper.selectOne(Wrappers.<ChatSessionDO>lambdaQuery()
                .eq(ChatSessionDO::getId, sessionId)
                .eq(ChatSessionDO::getDeleteFlag, DeleteFlagEnum.NORMAL.getCode()));
        if (sessionDO == null) {
            throw new ClientException("会话不存在");
        }
        if (!userId.equals(sessionDO.getUserId())) {
            throw new ClientException("无权访问该会话");
        }
        return sessionDO;
    }

    private void validateStreamRequest(ChatStreamReq requestParam) {
        if (requestParam == null || requestParam.getSessionId() == null
                || !StringUtils.hasText(requestParam.getMessage())) {
            throw new ClientException("会话ID和消息内容不能为空");
        }
    }

    private UserDO requireUser(Long userId) {
        UserDO userDO = userMapper.selectOne(Wrappers.<UserDO>lambdaQuery()
                .eq(UserDO::getId, userId)
                .eq(UserDO::getDeleteFlag, DeleteFlagEnum.NORMAL.getCode()));
        if (userDO == null) {
            throw new ClientException("用户不存在");
        }
        return userDO;
    }

    private Long currentUserId() {
        try {
            return Long.parseLong(UserContext.requireUser().getUserId());
        } catch (NumberFormatException ex) {
            throw new ClientException("当前登录用户ID非法");
        }
    }

    private List<ChatMessageDO> loadRecentMessages(Long sessionId) {
        int limit = memoryProperties.getRecentWindowSize() == null || memoryProperties.getRecentWindowSize() <= 0
                ? 12 : memoryProperties.getRecentWindowSize();
        LambdaQueryWrapper<ChatMessageDO> queryWrapper = Wrappers.<ChatMessageDO>lambdaQuery()
                .eq(ChatMessageDO::getDeleteFlag, DeleteFlagEnum.NORMAL.getCode())
                .eq(ChatMessageDO::getSessionId, sessionId)
                .orderByDesc(ChatMessageDO::getSequenceNo);
        Page<ChatMessageDO> page = new Page<>(1, limit, false);
        List<ChatMessageDO> recentMessages = chatMessageMapper.selectPage(page, queryWrapper).getRecords();
        Collections.reverse(recentMessages);
        return recentMessages;
    }

    private List<String> loadConversationContext(Long sessionId) {
        ChatSessionSummaryDO summary = chatSessionSummaryService.loadLatestSummary(sessionId);
        List<ChatMessageDO> recentMessages = loadRecentMessages(sessionId);
        List<String> recentTexts = toHistoryTexts(recentMessages);
        if (summary == null) {
            return recentTexts;
        }
        List<String> combined = new ArrayList<>();
        combined.add("[历史摘要]：" + summary.getSummaryText());
        combined.add("--- 以下为最近对话 ---");
        combined.addAll(recentTexts);
        return combined;
    }

    private String rewriteQuestion(String question, List<String> historyTexts, String traceId,
                                   Long sessionId, Long userId) {
        long rewriteStart = System.nanoTime();
        String rewrittenQuery = chatModelGateway.rewriteQuestion(question, historyTexts);
        log.info("[CHAT] Query改写: original={}, rewritten={}",
                shorten(question, 80), shorten(rewrittenQuery, 120));
        writeTrace(traceId, sessionId, userId, "REWRITE",
                "SUCCESS", elapsedMillis(rewriteStart), "rewrittenQuery=" + shorten(rewrittenQuery, 240));
        return rewrittenQuery;
    }

    private RouteDecision resolveRouteDecision(String question, List<String> historyTexts,
                                               String traceId, Long sessionId, Long userId) {
        List<ScoredLeafIntent> scoredLeaves = scoreLeafIntents(question, traceId, sessionId, userId);
        if (!scoredLeaves.isEmpty()) {
            List<ScoredLeafIntent> kbLeaves = scoredLeaves.stream()
                    .filter(each -> IntentNodeKindEnum.KB.getCode().equals(each.leaf().getKind()))
                    .filter(each -> each.leaf().getKbId() != null)
                    .toList();
            if (!kbLeaves.isEmpty()) {
                List<Long> knowledgeBaseIds = selectTopKnowledgeBaseIds(kbLeaves);
                String promptSnippet = mergePromptSnippetsForKnowledgeBases(kbLeaves, knowledgeBaseIds);
                String promptTemplate = resolveKnowledgePromptTemplate(kbLeaves, knowledgeBaseIds);
                log.info("[CHAT] 路由决策: route=KB, kbIds={}, leafCount={}", knowledgeBaseIds, kbLeaves.size());
                return RouteDecision.kb("KB_QA", kbLeaves, knowledgeBaseIds, promptSnippet, promptTemplate);
            }
            List<ScoredLeafIntent> systemLeaves = scoredLeaves.stream()
                    .filter(each -> IntentNodeKindEnum.SYSTEM.getCode().equals(each.leaf().getKind()))
                    .toList();
            if (!systemLeaves.isEmpty()) {
                ScoredLeafIntent topSystemLeaf = systemLeaves.stream()
                        .max(Comparator.comparingDouble(ScoredLeafIntent::score))
                        .orElse(null);
                if (topSystemLeaf != null) {
                    log.info("[CHAT] 路由决策: route=SYSTEM, intentCode={}", topSystemLeaf.leaf().getIntentCode());
                    return RouteDecision.system("CHITCHAT", topSystemLeaf.leaf());
                }
            }
        }

        long intentStart = System.nanoTime();
        String intentType = chatModelGateway.classifyQuestionIntent(question, historyTexts);
        log.info("[CHAT] 意图分类: intentType={}, elapsed={}ms", intentType, elapsedMillis(intentStart));
        writeTrace(traceId, sessionId, userId, "INTENT",
                "SUCCESS", elapsedMillis(intentStart), "intent=" + intentType + ",fallback=true");
        if ("CHITCHAT".equals(intentType)) {
            return RouteDecision.chitchat(intentType);
        }
        return RouteDecision.globalKb(intentType);
    }

    private SyncChatOutcome executeSyncRoute(RouteDecision routeDecision, String question, String rewrittenQuery,
                                             List<String> historyTexts, UserDO userDO,
                                             String traceId, Long sessionId, Long userId) {
        if (routeDecision.isChitchat()) {
            long generateStart = System.nanoTime();
            String answer = chatModelGateway.generateChitchatAnswer(question, historyTexts);
            writeTrace(traceId, sessionId, userId, "GENERATE",
                    "SUCCESS", elapsedMillis(generateStart), "intent=CHITCHAT");
            return new SyncChatOutcome(answer, List.of(), false);
        }
        if (routeDecision.isSystem()) {
            long generateStart = System.nanoTime();
            String answer = StringUtils.hasText(routeDecision.systemLeaf().getPromptTemplate())
                    ? chatModelGateway.generateChitchatAnswer(
                    question, historyTexts, routeDecision.systemLeaf().getPromptTemplate())
                    : chatModelGateway.generateChitchatAnswer(question, historyTexts);
            writeTrace(traceId, sessionId, userId, "GENERATE",
                    "SUCCESS", elapsedMillis(generateStart),
                    "intent=SYSTEM,intentCode=" + routeDecision.systemLeaf().getIntentCode());
            return new SyncChatOutcome(answer, List.of(), false);
        }

        RetrievalPlan retrievalPlan = executeRetrievalPlan(routeDecision, rewrittenQuery, userDO, traceId, sessionId, userId);
        if (retrievalPlan.refusal()) {
            writeTrace(traceId, sessionId, userId, "GENERATE", "CANCELLED", 1L, "rerank-empty");
            return new SyncChatOutcome(STATIC_REFUSAL_ANSWER, List.of(), false);
        }

        long generateStart = System.nanoTime();
        String answer = StringUtils.hasText(retrievalPlan.promptTemplate())
                ? chatModelGateway.generateAnswer(
                question,
                rewrittenQuery,
                historyTexts,
                retrievalPlan.rerankedChunks(),
                retrievalPlan.promptSnippet(),
                retrievalPlan.promptTemplate())
                : chatModelGateway.generateAnswer(
                question,
                rewrittenQuery,
                historyTexts,
                retrievalPlan.rerankedChunks(),
                retrievalPlan.promptSnippet());
        writeTrace(traceId, sessionId, userId, "GENERATE",
                "SUCCESS", elapsedMillis(generateStart),
                "citationCount=" + retrievalPlan.rerankedChunks().size());
        return new SyncChatOutcome(answer, toCitationResponses(retrievalPlan.rerankedChunks()), true);
    }

    private RetrievalPlan executeRetrievalPlan(RouteDecision routeDecision, String rewrittenQuery, UserDO userDO,
                                               String traceId, Long sessionId, Long userId) {
        if (!routeDecision.requiresRetrieval()) {
            return RetrievalPlan.noRetrieval();
        }

        List<RetrievedChunk> recalledChunks = retrieveChunks(routeDecision, rewrittenQuery, userDO, traceId, sessionId, userId);
        List<RetrievedChunk> rerankedChunks = rerankChunks(rewrittenQuery, recalledChunks, traceId, sessionId, userId);
        if (!rerankedChunks.isEmpty()) {
            return RetrievalPlan.success(rerankedChunks, routeDecision.promptSnippet(),
                    routeDecision.promptTemplate(), rewrittenQuery);
        }

        if (!routeDecision.isKbScoped()) {
            log.info("[CHAT] 重排序结果为空，返回静态拒绝回答");
            return RetrievalPlan.refusalResult();
        }

        log.info("[CHAT] KB范围检索为空，回退全局检索");
        List<RetrievedChunk> fallbackRecalled = retrieveGlobalChunks(userDO, rewrittenQuery, traceId, sessionId, userId, true);
        List<RetrievedChunk> fallbackReranked = rerankChunks(rewrittenQuery, fallbackRecalled, traceId, sessionId, userId);
        if (fallbackReranked.isEmpty()) {
            log.info("[CHAT] 全局回退后仍无结果，返回静态拒绝回答");
            return RetrievalPlan.refusalResult();
        }
        return RetrievalPlan.success(fallbackReranked, null, null, rewrittenQuery);
    }

    private List<RetrievedChunk> retrieveChunks(RouteDecision routeDecision, String rewrittenQuery, UserDO userDO,
                                                String traceId, Long sessionId, Long userId) {
        if (routeDecision.isKbScoped()) {
            return retrieveScopedChunks(userDO, rewrittenQuery, routeDecision.knowledgeBaseIds(),
                    traceId, sessionId, userId);
        }
        return retrieveGlobalChunks(userDO, rewrittenQuery, traceId, sessionId, userId, false);
    }

    private List<RetrievedChunk> retrieveScopedChunks(UserDO userDO, String rewrittenQuery, List<Long> knowledgeBaseIds,
                                                      String traceId, Long sessionId, Long userId) {
        long retrieveStart = System.nanoTime();
        List<RetrievedChunk> recalledChunks = ragRetrievalService.retrieveByKnowledgeBaseIds(userDO, rewrittenQuery, knowledgeBaseIds);
        log.info("[CHAT] 向量检索: scope=kbIds={}, candidateCount={}, elapsed={}ms",
                knowledgeBaseIds, recalledChunks.size(), elapsedMillis(retrieveStart));
        writeTrace(traceId, sessionId, userId, "RETRIEVE", "SUCCESS",
                elapsedMillis(retrieveStart),
                "candidateCount=" + recalledChunks.size() + ",kbIds=" + knowledgeBaseIds);
        return recalledChunks;
    }

    private List<RetrievedChunk> retrieveGlobalChunks(UserDO userDO, String rewrittenQuery,
                                                      String traceId, Long sessionId, Long userId,
                                                      boolean fallback) {
        long retrieveStart = System.nanoTime();
        List<RetrievedChunk> recalledChunks = ragRetrievalService.retrieve(userDO, rewrittenQuery);
        log.info("[CHAT] 向量检索: scope={}, candidateCount={}, elapsed={}ms",
                fallback ? "global-fallback" : "global", recalledChunks.size(), elapsedMillis(retrieveStart));
        writeTrace(traceId, sessionId, userId, "RETRIEVE", "SUCCESS",
                elapsedMillis(retrieveStart),
                "candidateCount=" + recalledChunks.size() + (fallback ? ",fallback=global" : ",global"));
        return recalledChunks;
    }

    private List<RetrievedChunk> rerankChunks(String rewrittenQuery, List<RetrievedChunk> recalledChunks,
                                              String traceId, Long sessionId, Long userId) {
        long rerankStart = System.nanoTime();
        List<RetrievedChunk> rerankedChunks = ragRetrievalService.rerank(rewrittenQuery, recalledChunks);
        log.info("[CHAT] 重排序: rerankCount={}, elapsed={}ms", rerankedChunks.size(), elapsedMillis(rerankStart));
        writeTrace(traceId, sessionId, userId, "RERANK",
                "SUCCESS", elapsedMillis(rerankStart), "rerankCount=" + rerankedChunks.size());
        return rerankedChunks;
    }

    private Flux<ChatStreamEventResp> buildStreamRouteEvents(RouteDecision routeDecision, RetrievalPlan retrievalPlan,
                                                             ChatStreamReq requestParam, ChatSessionDO sessionDO,
                                                             List<String> historyTexts, String traceId, Long userId,
                                                             boolean deepThinking, int assistantSequenceNo) {
        if (routeDecision.isChitchat()) {
            return streamChitchatRoute(traceId, requestParam.getSessionId(), userId, assistantSequenceNo,
                    requestParam.getMessage(), historyTexts, deepThinking, null);
        }
        if (routeDecision.isSystem()) {
            return streamChitchatRoute(traceId, requestParam.getSessionId(), userId, assistantSequenceNo,
                    requestParam.getMessage(), historyTexts, deepThinking, routeDecision.systemLeaf().getPromptTemplate());
        }
        if (retrievalPlan.refusal()) {
            return streamStaticAnswer(traceId, sessionDO, userId, assistantSequenceNo,
                    requestParam.getMessage(), STATIC_REFUSAL_ANSWER);
        }

        List<ChatStreamCitationResp> citations = toStreamCitationResponses(retrievalPlan.rerankedChunks());
        if (deepThinking) {
            return streamThinkingByCandidates(
                    traceId,
                    requestParam.getSessionId(),
                    userId,
                    assistantSequenceNo,
                    requestParam.getMessage(),
                    citations,
                    chatModelGateway.thinkingCandidateIds(),
                    (candidateId, ignored) -> StringUtils.hasText(retrievalPlan.promptTemplate())
                            ? chatModelGateway.streamThinkingKnowledgeAnswerByCandidate(
                            candidateId,
                            requestParam.getMessage(),
                            retrievalPlan.rewrittenQueryOr(requestParam.getMessage()),
                            historyTexts,
                            retrievalPlan.rerankedChunks(),
                            retrievalPlan.promptSnippet(),
                            retrievalPlan.promptTemplate())
                            : chatModelGateway.streamThinkingKnowledgeAnswerByCandidate(
                            candidateId,
                            requestParam.getMessage(),
                            retrievalPlan.rewrittenQueryOr(requestParam.getMessage()),
                            historyTexts,
                            retrievalPlan.rerankedChunks(),
                            retrievalPlan.promptSnippet()));
        }
        return streamByCandidates(
                traceId,
                requestParam.getSessionId(),
                userId,
                assistantSequenceNo,
                requestParam.getMessage(),
                citations,
                chatModelGateway.streamingCandidateIds(),
                (candidateId, ignored) -> StringUtils.hasText(retrievalPlan.promptTemplate())
                        ? chatModelGateway.streamKnowledgeAnswerByCandidate(
                        candidateId,
                        requestParam.getMessage(),
                        retrievalPlan.rewrittenQueryOr(requestParam.getMessage()),
                        historyTexts,
                        retrievalPlan.rerankedChunks(),
                        retrievalPlan.promptSnippet(),
                        retrievalPlan.promptTemplate())
                        : chatModelGateway.streamKnowledgeAnswerByCandidate(
                        candidateId,
                        requestParam.getMessage(),
                        retrievalPlan.rewrittenQueryOr(requestParam.getMessage()),
                        historyTexts,
                        retrievalPlan.rerankedChunks(),
                        retrievalPlan.promptSnippet()));
    }

    private Flux<ChatStreamEventResp> streamChitchatRoute(String traceId, Long sessionId, Long userId,
                                                          int assistantSequenceNo, String question,
                                                          List<String> historyTexts, boolean deepThinking,
                                                          String promptTemplate) {
        if (deepThinking) {
            return streamThinkingByCandidates(
                    traceId,
                    sessionId,
                    userId,
                    assistantSequenceNo,
                    question,
                    List.of(),
                    chatModelGateway.thinkingCandidateIds(),
                    (candidateId, ignored) -> StringUtils.hasText(promptTemplate)
                            ? chatModelGateway.streamThinkingChitchatByCandidate(
                            candidateId, question, historyTexts, promptTemplate)
                            : chatModelGateway.streamThinkingChitchatByCandidate(candidateId, question, historyTexts));
        }
        return streamByCandidates(
                traceId,
                sessionId,
                userId,
                assistantSequenceNo,
                question,
                List.of(),
                chatModelGateway.streamingCandidateIds(),
                (candidateId, ignored) -> StringUtils.hasText(promptTemplate)
                        ? chatModelGateway.streamChitchatByCandidate(
                        candidateId, question, historyTexts, promptTemplate)
                        : chatModelGateway.streamChitchatByCandidate(candidateId, question, historyTexts));
    }

    private SequenceReservation reserveMessageSequences(Long sessionId) {
        long latestSequenceNo = loadLatestSequenceNo(sessionId);
        RAtomicLong sequenceCounter = redissonClient.getAtomicLong(messageSequenceCounterKey(sessionId));
        long currentCounter = sequenceCounter.get();
        if (currentCounter < latestSequenceNo) {
            sequenceCounter.compareAndSet(currentCounter, latestSequenceNo);
        }
        long assistantSequenceNo = sequenceCounter.addAndGet(2L);
        return new SequenceReservation(
                Math.toIntExact(assistantSequenceNo - 1),
                Math.toIntExact(assistantSequenceNo));
    }

    private long loadLatestSequenceNo(Long sessionId) {
        Page<ChatMessageDO> page = new Page<>(1, 1, false);
        List<ChatMessageDO> records = chatMessageMapper.selectPage(page, Wrappers.<ChatMessageDO>lambdaQuery()
                        .eq(ChatMessageDO::getDeleteFlag, DeleteFlagEnum.NORMAL.getCode())
                        .eq(ChatMessageDO::getSessionId, sessionId)
                        .orderByDesc(ChatMessageDO::getSequenceNo))
                .getRecords();
        if (records == null || records.isEmpty() || records.get(0).getSequenceNo() == null) {
            return 0L;
        }
        return records.get(0).getSequenceNo();
    }

    private String messageSequenceCounterKey(Long sessionId) {
        return "chat:message:sequence:" + sessionId;
    }

    private List<String> toHistoryTexts(List<ChatMessageDO> messages) {
        return messages.stream()
                .map(each -> each.getRole() + ": " + each.getContent())
                .toList();
    }

    private List<ChatCitationResp> toCitationResponses(List<RetrievedChunk> retrievedChunks) {
        return IntStream.range(0, retrievedChunks.size())
                .mapToObj(index -> {
                    RetrievedChunk each = retrievedChunks.get(index);
                    return new ChatCitationResp()
                            .setIndex(index + 1)
                            .setDocumentId(each.documentId())
                            .setDocumentTitle(each.documentTitle())
                            .setChunkId(each.chunkId())
                            .setChunkNo(each.chunkNo())
                            .setReferenceLabel(each.documentTitle() + "（切片#" + each.chunkNo() + "）")
                            .setSnippet(shorten(each.effectiveContent(), 120));
                })
                .toList();
    }

    private List<ChatStreamCitationResp> toStreamCitationResponses(List<RetrievedChunk> retrievedChunks) {
        return IntStream.range(0, retrievedChunks.size())
                .mapToObj(index -> {
                    RetrievedChunk each = retrievedChunks.get(index);
                    return new ChatStreamCitationResp()
                            .setIndex(index + 1)
                            .setDocumentId(each.documentId())
                            .setDocumentTitle(each.documentTitle())
                            .setChunkId(each.chunkId())
                            .setChunkNo(each.chunkNo())
                            .setReferenceLabel(each.documentTitle() + "（切片#" + each.chunkNo() + "）")
                            .setSnippet(shorten(each.effectiveContent(), 120));
                })
                .toList();
    }

    private void persistUserMessage(ChatStreamReq requestParam, Long userId, String traceId, int sequenceNo) {
        ChatMessageDO userMessage = new ChatMessageDO()
                .setSessionId(requestParam.getSessionId())
                .setUserId(userId)
                .setRole("USER")
                .setContent(requestParam.getMessage())
                .setContentType(ChatMessageContentTypeEnum.TEXT.getCode())
                .setSequenceNo(sequenceNo)
                .setTraceId(traceId);
        chatMessageMapper.insert(userMessage);
    }

    private Flux<ChatStreamEventResp> streamStaticAnswer(String traceId, ChatSessionDO sessionDO, Long userId,
                                                         int assistantSequenceNo, String question, String answer) {
        long generateStart = System.nanoTime();
        List<String> candidateIds = chatModelGateway.streamingCandidateIds();
        String candidateId = candidateIds.isEmpty() ? "static" : candidateIds.get(0);
        ChatModelInfoRecord modelInfo = candidateIds.isEmpty() ? null : chatModelGateway.candidateInfo(candidateId);
        ChatMessageDO assistantMessage = persistAssistantMessage(
                sessionDO.getId(), userId, assistantSequenceNo, traceId, answer, modelInfo);
        updateSessionLastActiveAt(sessionDO.getId());
        chatSessionSummaryService.maybeSummarize(sessionDO.getId(), assistantSequenceNo);
        writeTrace(traceId, sessionDO.getId(), userId, "GENERATE", "SUCCESS", elapsedMillis(generateStart), "intent=STATIC_REFUSAL");
        return Flux.just(
                ChatStreamEventResp.messageStart(traceId, sessionDO.getId(), candidateId, 1),
                ChatStreamEventResp.delta(traceId, sessionDO.getId(), answer),
                ChatStreamEventResp.messageEnd(traceId, sessionDO.getId(), assistantMessage.getId()));
    }

    private Flux<ChatStreamEventResp> streamByCandidates(String traceId, Long sessionId, Long userId,
                                                         int assistantSequenceNo, String question,
                                                         List<ChatStreamCitationResp> citations,
                                                         List<String> candidateIds,
                                                         CandidateStreamProvider streamProvider) {
        if (candidateIds == null || candidateIds.isEmpty()) {
            return Flux.just(ChatStreamEventResp.error(traceId, sessionId,
                    BaseErrorCode.SERVICE_ERROR.code(), "当前没有可用的聊天模型候选"));
        }
        long generationStart = System.nanoTime();
        return streamByCandidate(traceId, sessionId, userId, assistantSequenceNo, question, citations,
                candidateIds, 0, 1, generationStart, streamProvider);
    }

    private Flux<ChatStreamEventResp> streamByCandidate(String traceId, Long sessionId, Long userId,
                                                        int assistantSequenceNo, String question,
                                                        List<ChatStreamCitationResp> citations,
                                                        List<String> candidateIds, int index, int attemptNo,
                                                        long generationStartNanos,
                                                        CandidateStreamProvider streamProvider) {
        if (index >= candidateIds.size()) {
            log.warn("[MODEL] 所有流式候选模型均已失败: candidateCount={}, elapsed={}ms", candidateIds.size(), elapsedMillis(generationStartNanos));
            writeTrace(traceId, sessionId, userId, "GENERATE", "FAILED", elapsedMillis(generationStartNanos), "all-candidates-exhausted");
            return Flux.just(ChatStreamEventResp.error(traceId, sessionId,
                    BaseErrorCode.SERVICE_ERROR.code(), "当前没有可用的聊天模型候选"));
        }
        String candidateId = candidateIds.get(index);
        long attemptStart = System.nanoTime();
        if (!chatModelGateway.tryAcquireStreamingCandidate(candidateId)) {
            log.warn("[MODEL] 流式候选模型熔断跳过: candidateId={}, 跳至下一个", candidateId);
            writeTrace(traceId, sessionId, userId, "STREAM_CANDIDATE",
                    "SKIPPED", elapsedMillis(attemptStart), "candidateId=" + candidateId + ",reason=skipped-circuit-open");
            return streamByCandidate(traceId, sessionId, userId, assistantSequenceNo, question,
                    citations, candidateIds, index + 1, attemptNo, generationStartNanos, streamProvider);
        }
        log.info("[MODEL] 流式候选模型开始尝试: candidateId={}, attemptNo={}", candidateId, attemptNo);

        AtomicBoolean sawDelta = new AtomicBoolean(false);
        AtomicBoolean messageStarted = new AtomicBoolean(false);
        StringBuilder answerBuilder = new StringBuilder();

        Flux<String> contentFlux = applyStreamTimeouts(streamProvider.stream(candidateId, question));
        Flux<ChatStreamEventResp> attemptFlux = contentFlux.concatMap(each -> {
                    sawDelta.set(true);
                    answerBuilder.append(each);
                    ChatStreamEventResp delta = ChatStreamEventResp.delta(traceId, sessionId, each);
                    if (messageStarted.compareAndSet(false, true)) {
                        return Flux.just(ChatStreamEventResp.messageStart(traceId, sessionId, candidateId, attemptNo), delta);
                    }
                    return Flux.just(delta);
                })
                .concatWith(Flux.defer(() -> {
                    if (!sawDelta.get()) {
                        return Flux.error(new IllegalStateException("聊天模型流式返回为空"));
                    }
                    return offloadBlocking(() -> {
                        chatModelGateway.markStreamingCandidateSuccess(candidateId);
                        ChatModelInfoRecord modelInfo = chatModelGateway.candidateInfo(candidateId);
                        ChatMessageDO assistantMessage = persistAssistantMessage(
                                sessionId, userId, assistantSequenceNo, traceId, answerBuilder.toString(), modelInfo);
                        updateSessionLastActiveAt(sessionId);
                        chatSessionSummaryService.maybeSummarize(sessionId, assistantSequenceNo);
                        writeTrace(traceId, sessionId, userId, "STREAM_CANDIDATE",
                                "SUCCESS", elapsedMillis(attemptStart), "candidateId=" + candidateId + ",attemptNo=" + attemptNo);
                        return assistantMessage;
                    }).flatMapMany(assistantMessage -> {
                        Flux<ChatStreamEventResp> citationFlux = citations == null || citations.isEmpty()
                                ? Flux.empty()
                                : Flux.just(ChatStreamEventResp.citation(traceId, sessionId, citations));
                        return citationFlux.concatWithValues(ChatStreamEventResp.messageEnd(traceId, sessionId, assistantMessage.getId()));
                    }).onErrorResume(ex -> handleStreamCompletionFailure(traceId, sessionId, candidateId, ex));
                }))
                .onErrorResume(ex -> {
                    chatModelGateway.markStreamingCandidateFailure(candidateId);
                    String reason = resolveStreamFailureReason(ex, sawDelta.get());
                    log.warn("[MODEL] 流式候选模型失败，熔断并切换下一个: candidateId={}, reason={}, error={}",
                            candidateId, reason, ex.getMessage());
                    writeTrace(traceId, sessionId, userId, "STREAM_CANDIDATE",
                            "FAILED", elapsedMillis(attemptStart), "candidateId=" + candidateId + ",reason=" + reason);
                    Flux<ChatStreamEventResp> nextFlux = streamByCandidate(traceId, sessionId, userId, assistantSequenceNo,
                            question, citations, candidateIds, index + 1, attemptNo + 1, generationStartNanos, streamProvider);
                    if (sawDelta.get()) {
                        return Flux.just(ChatStreamEventResp.reset(traceId, sessionId, candidateId, reason))
                                .concatWith(nextFlux);
                    }
                    return nextFlux;
                });
        return attemptFlux;
    }

    private Flux<ChatStreamEventResp> streamThinkingByCandidates(String traceId, Long sessionId, Long userId,
                                                                 int assistantSequenceNo, String question,
                                                                 List<ChatStreamCitationResp> citations,
                                                                 List<String> candidateIds,
                                                                 CandidateThinkingStreamProvider streamProvider) {
        if (candidateIds == null || candidateIds.isEmpty()) {
            return Flux.just(ChatStreamEventResp.error(traceId, sessionId,
                    BaseErrorCode.SERVICE_ERROR.code(), "当前没有可用的深度思考模型候选"));
        }
        long generationStart = System.nanoTime();
        return streamThinkingByCandidate(traceId, sessionId, userId, assistantSequenceNo, question, citations,
                candidateIds, 0, 1, generationStart, streamProvider);
    }

    private Flux<ChatStreamEventResp> streamThinkingByCandidate(String traceId, Long sessionId, Long userId,
                                                                int assistantSequenceNo, String question,
                                                                List<ChatStreamCitationResp> citations,
                                                                List<String> candidateIds, int index, int attemptNo,
                                                                long generationStartNanos,
                                                                CandidateThinkingStreamProvider streamProvider) {
        if (index >= candidateIds.size()) {
            log.warn("[MODEL] 所有深度思考候选模型均已失败: candidateCount={}, elapsed={}ms", candidateIds.size(), elapsedMillis(generationStartNanos));
            writeTrace(traceId, sessionId, userId, "GENERATE", "FAILED", elapsedMillis(generationStartNanos), "all-thinking-candidates-exhausted");
            return Flux.just(ChatStreamEventResp.error(traceId, sessionId,
                    BaseErrorCode.SERVICE_ERROR.code(), "当前没有可用的深度思考模型候选"));
        }
        String candidateId = candidateIds.get(index);
        long attemptStart = System.nanoTime();
        if (!chatModelGateway.tryAcquireStreamingCandidate(candidateId)) {
            log.warn("[MODEL] 深度思考候选模型熔断跳过: candidateId={}, 跳至下一个", candidateId);
            writeTrace(traceId, sessionId, userId, "STREAM_CANDIDATE",
                    "SKIPPED", elapsedMillis(attemptStart), "candidateId=" + candidateId + ",reason=skipped-circuit-open");
            return streamThinkingByCandidate(traceId, sessionId, userId, assistantSequenceNo, question,
                    citations, candidateIds, index + 1, attemptNo, generationStartNanos, streamProvider);
        }
        log.info("[MODEL] 深度思考候选模型开始尝试: candidateId={}, attemptNo={}", candidateId, attemptNo);

        AtomicBoolean sawDelta = new AtomicBoolean(false);
        AtomicBoolean messageStarted = new AtomicBoolean(false);
        StringBuilder answerBuilder = new StringBuilder();
        StringBuilder thinkingBuilder = new StringBuilder();
        AtomicReference<Long> thinkingStartNanos = new AtomicReference<>();

        Flux<StreamContent> contentFlux = applyThinkingStreamTimeouts(streamProvider.stream(candidateId, question));
        Flux<ChatStreamEventResp> attemptFlux = contentFlux.concatMap(sc -> {
                    List<ChatStreamEventResp> events = new java.util.ArrayList<>();
                    if (sc.hasThinking()) {
                        if (thinkingStartNanos.get() == null) {
                            thinkingStartNanos.set(System.nanoTime());
                        }
                        thinkingBuilder.append(sc.thinking());
                        events.add(ChatStreamEventResp.thinkingDelta(traceId, sessionId, sc.thinking()));
                    }
                    if (sc.hasContent()) {
                        sawDelta.set(true);
                        answerBuilder.append(sc.content());
                        if (messageStarted.compareAndSet(false, true)) {
                            events.add(ChatStreamEventResp.messageStart(traceId, sessionId, candidateId, attemptNo));
                        }
                        events.add(ChatStreamEventResp.delta(traceId, sessionId, sc.content()));
                    }
                    return Flux.fromIterable(events);
                })
                .concatWith(Flux.defer(() -> {
                    if (!sawDelta.get()) {
                        return Flux.error(new IllegalStateException("聊天模型流式返回为空"));
                    }
                    return offloadBlocking(() -> {
                        chatModelGateway.markStreamingCandidateSuccess(candidateId);
                        ChatModelInfoRecord modelInfo = chatModelGateway.candidateInfo(candidateId);
                        Long thinkingDurationMs = thinkingStartNanos.get() == null ? null
                                : Math.max(1L, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - thinkingStartNanos.get()));
                        ChatMessageDO assistantMessage = persistAssistantMessage(
                                sessionId, userId, assistantSequenceNo, traceId,
                                answerBuilder.toString(), modelInfo,
                                thinkingBuilder.toString(), thinkingDurationMs);
                        updateSessionLastActiveAt(sessionId);
                        chatSessionSummaryService.maybeSummarize(sessionId, assistantSequenceNo);
                        writeTrace(traceId, sessionId, userId, "STREAM_CANDIDATE",
                                "SUCCESS", elapsedMillis(attemptStart), "candidateId=" + candidateId + ",attemptNo=" + attemptNo + ",thinking=" + (thinkingDurationMs != null));
                        return assistantMessage;
                    }).flatMapMany(assistantMessage -> {
                        Flux<ChatStreamEventResp> citationFlux = citations == null || citations.isEmpty()
                                ? Flux.empty()
                                : Flux.just(ChatStreamEventResp.citation(traceId, sessionId, citations));
                        return citationFlux.concatWithValues(ChatStreamEventResp.messageEnd(traceId, sessionId, assistantMessage.getId()));
                    }).onErrorResume(ex -> handleStreamCompletionFailure(traceId, sessionId, candidateId, ex));
                }))
                .onErrorResume(ex -> {
                    chatModelGateway.markStreamingCandidateFailure(candidateId);
                    String reason = resolveStreamFailureReason(ex, sawDelta.get());
                    log.warn("[MODEL] 深度思考候选模型失败，熔断并切换下一个: candidateId={}, reason={}, error={}",
                            candidateId, reason, ex.getMessage());
                    writeTrace(traceId, sessionId, userId, "STREAM_CANDIDATE",
                            "FAILED", elapsedMillis(attemptStart), "candidateId=" + candidateId + ",reason=" + reason);
                    Flux<ChatStreamEventResp> nextFlux = streamThinkingByCandidate(traceId, sessionId, userId, assistantSequenceNo,
                            question, citations, candidateIds, index + 1, attemptNo + 1, generationStartNanos, streamProvider);
                    if (sawDelta.get()) {
                        return Flux.just(ChatStreamEventResp.reset(traceId, sessionId, candidateId, reason))
                                .concatWith(nextFlux);
                    }
                    return nextFlux;
                });
        return attemptFlux;
    }

    private Flux<StreamContent> applyThinkingStreamTimeouts(Flux<StreamContent> source) {
        Integer firstTokenTimeoutMillis = aiProperties.getCircuitBreaker().getFirstTokenTimeoutMillis();
        Integer streamChunkIdleTimeoutMillis = aiProperties.getCircuitBreaker().getStreamChunkIdleTimeoutMillis();
        if ((firstTokenTimeoutMillis == null || firstTokenTimeoutMillis <= 0)
                && (streamChunkIdleTimeoutMillis == null || streamChunkIdleTimeoutMillis <= 0)) {
            return source;
        }
        Mono<Long> firstTimeout = firstTokenTimeoutMillis != null && firstTokenTimeoutMillis > 0
                ? Mono.delay(Duration.ofMillis(firstTokenTimeoutMillis))
                : Mono.never();
        if (firstTokenTimeoutMillis != null && firstTokenTimeoutMillis > 0
                && streamChunkIdleTimeoutMillis != null && streamChunkIdleTimeoutMillis > 0) {
            return source.timeout(firstTimeout, ignored -> Mono.delay(Duration.ofMillis(streamChunkIdleTimeoutMillis)));
        }
        if (firstTokenTimeoutMillis != null && firstTokenTimeoutMillis > 0) {
            return source.timeout(firstTimeout, ignored -> Mono.never());
        }
        return source.timeout(Mono.never(), ignored -> Mono.delay(Duration.ofMillis(streamChunkIdleTimeoutMillis)));
    }

    private Flux<String> applyStreamTimeouts(Flux<String> source) {
        Integer firstTokenTimeoutMillis = aiProperties.getCircuitBreaker().getFirstTokenTimeoutMillis();
        Integer streamChunkIdleTimeoutMillis = aiProperties.getCircuitBreaker().getStreamChunkIdleTimeoutMillis();
        if ((firstTokenTimeoutMillis == null || firstTokenTimeoutMillis <= 0)
                && (streamChunkIdleTimeoutMillis == null || streamChunkIdleTimeoutMillis <= 0)) {
            return source;
        }
        Mono<Long> firstTimeout = firstTokenTimeoutMillis != null && firstTokenTimeoutMillis > 0
                ? Mono.delay(Duration.ofMillis(firstTokenTimeoutMillis))
                : Mono.never();
        if (firstTokenTimeoutMillis != null && firstTokenTimeoutMillis > 0
                && streamChunkIdleTimeoutMillis != null && streamChunkIdleTimeoutMillis > 0) {
            return source.timeout(firstTimeout, ignored -> Mono.delay(Duration.ofMillis(streamChunkIdleTimeoutMillis)));
        }
        if (firstTokenTimeoutMillis != null && firstTokenTimeoutMillis > 0) {
            return source.timeout(firstTimeout, ignored -> Mono.never());
        }
        return source.timeout(Mono.never(), ignored -> Mono.delay(Duration.ofMillis(streamChunkIdleTimeoutMillis)));
    }

    private <T> Mono<T> offloadBlocking(Callable<T> callable) {
        return Mono.fromCallable(callable)
                .subscribeOn(Schedulers.boundedElastic());
    }

    private Flux<ChatStreamEventResp> handleStreamCompletionFailure(String traceId, Long sessionId,
                                                                    String candidateId, Throwable throwable) {
        log.error("流式响应收尾持久化失败: traceId={}, sessionId={}, candidateId={}",
                traceId, sessionId, candidateId, throwable);
        return Flux.just(ChatStreamEventResp.error(
                traceId,
                sessionId,
                BaseErrorCode.SERVICE_ERROR.code(),
                "对话结果保存失败，请稍后重试"));
    }

    private ChatMessageDO persistAssistantMessage(Long sessionId, Long userId, int sequenceNo, String traceId,
                                                  String answer, ChatModelInfoRecord modelInfo) {
        return persistAssistantMessage(sessionId, userId, sequenceNo, traceId, answer, modelInfo, null, null);
    }

    private ChatMessageDO persistAssistantMessage(Long sessionId, Long userId, int sequenceNo, String traceId,
                                                  String answer, ChatModelInfoRecord modelInfo,
                                                  String thinkingContent, Long thinkingDurationMs) {
        ChatMessageDO assistantMessage = new ChatMessageDO()
                .setSessionId(sessionId)
                .setUserId(userId)
                .setRole("ASSISTANT")
                .setContent(answer)
                .setContentType(ChatMessageContentTypeEnum.TEXT.getCode())
                .setSequenceNo(sequenceNo)
                .setTraceId(traceId)
                .setModelProvider(modelInfo == null ? null : modelInfo.provider())
                .setModelName(modelInfo == null ? null : modelInfo.modelName())
                .setThinkingContent(thinkingContent)
                .setThinkingDurationMs(thinkingDurationMs);
        chatMessageMapper.insert(assistantMessage);
        return assistantMessage;
    }

    private void updateSessionLastActiveAt(Long sessionId) {
        ChatSessionDO updateSession = new ChatSessionDO();
        updateSession.setId(sessionId);
        updateSession.setLastActiveAt(new Date());
        chatSessionMapper.updateById(updateSession);
    }

    private String resolveStreamFailureReason(Throwable throwable, boolean hasOutput) {
        if (throwable instanceof TimeoutException) {
            return hasOutput ? "idle-timeout" : "first-token-timeout";
        }
        return "stream-error";
    }

    private void writeTrace(String traceId, Long sessionId, Long userId, String stage,
                            String status, long latencyMs, String payload) {
        if (!Boolean.TRUE.equals(traceProperties.getEnabled())) {
            return;
        }
        try {
            QaTraceLogDO traceLogDO = new QaTraceLogDO()
                    .setTraceId(traceId)
                    .setSessionId(sessionId)
                    .setUserId(userId)
                    .setStage(stage)
                    .setStatus(status)
                    .setLatencyMs(latencyMs)
                    .setPayloadRef(Boolean.TRUE.equals(traceProperties.getLogPayload()) ? shorten(payload, 512) : null);
            qaTraceLogMapper.insert(traceLogDO);
        } catch (Exception ex) {
            log.warn("写入链路追踪日志失败: traceId={}, sessionId={}, stage={}, status={}",
                    traceId, sessionId, stage, status, ex);
        }
    }

    private long elapsedMillis(long startNanos) {
        long elapsedNanos = System.nanoTime() - startNanos;
        if (elapsedNanos <= 0) {
            return 0L;
        }
        long millis = TimeUnit.NANOSECONDS.toMillis(elapsedNanos);
        return Math.max(1L, millis);
    }

    private String shorten(String text, int maxLength) {
        if (!StringUtils.hasText(text) || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }

    private List<ScoredLeafIntent> scoreLeafIntents(String question, String traceId, Long sessionId, Long userId) {
        List<IntentNodeTreeResp> tree = intentNodeService.getTree();
        List<IntentNodeTreeResp> leaves = collectLeafNodes(tree);
        log.info("[INTENT] 加载意图树: leafCount={}", leaves.size());
        if (leaves.isEmpty()) {
            return List.of();
        }
        long intentScoreStart = System.nanoTime();
        List<ScoredLeafIntent> matches = new ArrayList<>();
        for (IntentNodeTreeResp leaf : leaves) {
            try {
                double score = chatModelGateway.scoreLeafIntent(question, leaf);
                log.info("[INTENT] 叶子打分: intentCode={}, name={}, score={}", leaf.getIntentCode(), leaf.getName(), score);
                if (score >= INTENT_SCORE_THRESHOLD) {
                    matches.add(new ScoredLeafIntent(leaf, score));
                }
            } catch (Exception e) {
                log.warn("意图打分异常, intentCode={}", leaf.getIntentCode(), e);
            }
        }
        matches.sort(Comparator.comparingDouble(ScoredLeafIntent::score).reversed());
        if (matches.isEmpty()) {
            log.info("[INTENT] 无匹配叶子节点");
            writeTrace(traceId, sessionId, userId, "INTENT_SCORE", "SUCCESS",
                    elapsedMillis(intentScoreStart), "leafCount=" + leaves.size() + ",no-match");
        } else {
            log.info("[INTENT] 命中叶子节点: {}", matches.stream()
                    .map(each -> each.leaf().getIntentCode() + "=" + each.score())
                    .toList());
            writeTrace(traceId, sessionId, userId, "INTENT_SCORE", "SUCCESS",
                    elapsedMillis(intentScoreStart),
                    "matchCount=" + matches.size() + ",top=" + matches.get(0).leaf().getIntentCode());
        }
        return matches;
    }

    private List<IntentNodeTreeResp> collectLeafNodes(List<IntentNodeTreeResp> nodes) {
        List<IntentNodeTreeResp> leaves = new ArrayList<>();
        if (nodes == null) {
            return leaves;
        }
        for (IntentNodeTreeResp node : nodes) {
            if (node.getChildren() == null || node.getChildren().isEmpty()) {
                if (Integer.valueOf(1).equals(node.getEnabled())) {
                    leaves.add(node);
                }
            } else {
                leaves.addAll(collectLeafNodes(node.getChildren()));
            }
        }
        return leaves;
    }

    private List<Long> selectTopKnowledgeBaseIds(List<ScoredLeafIntent> kbLeaves) {
        Map<Long, Double> bestScoreByKbId = new LinkedHashMap<>();
        for (ScoredLeafIntent each : kbLeaves) {
            bestScoreByKbId.merge(each.leaf().getKbId(), each.score(), Math::max);
        }
        return bestScoreByKbId.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(MAX_ROUTE_KB_COUNT)
                .map(Map.Entry::getKey)
                .toList();
    }

    private String mergePromptSnippetsForKnowledgeBases(List<ScoredLeafIntent> kbLeaves, List<Long> knowledgeBaseIds) {
        LinkedHashSet<String> snippets = new LinkedHashSet<>();
        kbLeaves.stream()
                .filter(each -> knowledgeBaseIds.contains(each.leaf().getKbId()))
                .sorted(Comparator.comparingDouble(ScoredLeafIntent::score).reversed())
                .map(each -> each.leaf().getPromptSnippet())
                .filter(StringUtils::hasText)
                .map(String::trim)
                .forEach(snippets::add);
        return snippets.isEmpty() ? null : String.join("\n", snippets);
    }

    private String resolveKnowledgePromptTemplate(List<ScoredLeafIntent> kbLeaves, List<Long> knowledgeBaseIds) {
        if (knowledgeBaseIds.size() != 1) {
            return null;
        }
        Long targetKbId = knowledgeBaseIds.get(0);
        return kbLeaves.stream()
                .filter(each -> targetKbId.equals(each.leaf().getKbId()))
                .sorted(Comparator.comparingDouble(ScoredLeafIntent::score).reversed())
                .map(each -> each.leaf().getPromptTemplate())
                .filter(StringUtils::hasText)
                .map(String::trim)
                .findFirst()
                .orElse(null);
    }

    private record SequenceReservation(int userSequenceNo, int assistantSequenceNo) {
    }

    private record ScoredLeafIntent(IntentNodeTreeResp leaf, double score) {
    }

    private record SyncChatOutcome(String answer, List<ChatCitationResp> citations, boolean knowledgeHit) {
    }

    private record RetrievalPlan(List<RetrievedChunk> rerankedChunks, String promptSnippet,
                                 String promptTemplate, boolean refusal, String rewrittenQuery) {

        private static RetrievalPlan noRetrieval() {
            return new RetrievalPlan(List.of(), null, null, false, null);
        }

        private static RetrievalPlan success(List<RetrievedChunk> rerankedChunks, String promptSnippet,
                                             String promptTemplate, String rewrittenQuery) {
            return new RetrievalPlan(rerankedChunks, promptSnippet, promptTemplate, false, rewrittenQuery);
        }

        private static RetrievalPlan refusalResult() {
            return new RetrievalPlan(List.of(), null, null, true, null);
        }

        public String rewrittenQueryOr(String fallback) {
            return StringUtils.hasText(rewrittenQuery) ? rewrittenQuery : fallback;
        }
    }

    private record RouteDecision(String routeType, String intentType, List<Long> knowledgeBaseIds,
                                 IntentNodeTreeResp systemLeaf, String promptSnippet,
                                 String promptTemplate) {

        private static RouteDecision chitchat(String intentType) {
            return new RouteDecision("CHITCHAT", intentType, List.of(), null, null, null);
        }

        private static RouteDecision globalKb(String intentType) {
            return new RouteDecision("GLOBAL_KB", intentType, List.of(), null, null, null);
        }

        private static RouteDecision kb(String intentType, List<ScoredLeafIntent> ignoredLeaves,
                                        List<Long> knowledgeBaseIds, String promptSnippet,
                                        String promptTemplate) {
            return new RouteDecision("KB", intentType, knowledgeBaseIds, null, promptSnippet, promptTemplate);
        }

        private static RouteDecision system(String intentType, IntentNodeTreeResp systemLeaf) {
            return new RouteDecision("SYSTEM", intentType, List.of(), systemLeaf,
                    systemLeaf.getPromptSnippet(), systemLeaf.getPromptTemplate());
        }

        private boolean isChitchat() {
            return "CHITCHAT".equals(routeType);
        }

        private boolean isSystem() {
            return "SYSTEM".equals(routeType);
        }

        private boolean isKbScoped() {
            return "KB".equals(routeType);
        }

        private boolean requiresRetrieval() {
            return isKbScoped() || "GLOBAL_KB".equals(routeType);
        }

        private boolean requiresRewrite() {
            return requiresRetrieval() || isSystem();
        }
    }
}
