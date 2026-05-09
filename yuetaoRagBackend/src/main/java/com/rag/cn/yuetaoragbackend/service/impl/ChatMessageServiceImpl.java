package com.rag.cn.yuetaoragbackend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.rag.cn.yuetaoragbackend.config.enums.ChatMessageContentTypeEnum;
import com.rag.cn.yuetaoragbackend.config.enums.ChatSessionStatusEnum;
import com.rag.cn.yuetaoragbackend.config.enums.DeleteFlagEnum;
import com.rag.cn.yuetaoragbackend.config.properties.AiProperties;
import com.rag.cn.yuetaoragbackend.config.properties.MemoryProperties;
import com.rag.cn.yuetaoragbackend.config.properties.TraceProperties;
import com.rag.cn.yuetaoragbackend.config.record.ChatModelInfoRecord;
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
import com.rag.cn.yuetaoragbackend.dto.req.CreateChatMessageReq;
import com.rag.cn.yuetaoragbackend.dto.resp.*;
import com.rag.cn.yuetaoragbackend.framework.context.UserContext;
import com.rag.cn.yuetaoragbackend.framework.errorcode.BaseErrorCode;
import com.rag.cn.yuetaoragbackend.framework.exception.ClientException;
import com.rag.cn.yuetaoragbackend.service.ChatMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;
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
    private final ExecutorService chatStreamExecutor;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChatResp chat(ChatReq requestParam) {
        if (requestParam == null || requestParam.getSessionId() == null
                || !StringUtils.hasText(requestParam.getMessage())) {
            throw new ClientException("会话ID和消息内容不能为空");
        }
        Long userId = currentUserId();

        ChatSessionDO sessionDO = requireSession(requestParam.getSessionId(), userId);
        UserDO userDO = requireUser(userId);

        String traceId = StringUtils.hasText(requestParam.getTraceId()) ? requestParam.getTraceId() : UUID.randomUUID().toString();

        List<ChatMessageDO> recentMessages = loadRecentMessages(requestParam.getSessionId());
        int nextSequenceNo = nextSequenceNo(recentMessages);
        List<String> historyTexts = toHistoryTexts(recentMessages);

        long intentStart = System.nanoTime();
        String intentType = chatModelGateway.classifyQuestionIntent(requestParam.getMessage(), historyTexts);
        writeTrace(traceId, requestParam.getSessionId(), userId, "INTENT",
                "SUCCESS",
                elapsedMillis(intentStart),
                "intent=" + intentType);

        String answer;
        String rewrittenQuery = requestParam.getMessage();
        List<ChatCitationResp> citations = List.of();
        boolean knowledgeHit = false;

        if ("CHITCHAT".equals(intentType)) {
            long generateStart = System.nanoTime();
            answer = chatModelGateway.generateChitchatAnswer(requestParam.getMessage(), historyTexts);
            writeTrace(traceId, requestParam.getSessionId(), userId, "GENERATE",
                    "SUCCESS",
                    elapsedMillis(generateStart),
                    "intent=CHITCHAT");
        } else {
            long rewriteStart = System.nanoTime();
            rewrittenQuery = chatModelGateway.rewriteQuestion(requestParam.getMessage(), historyTexts);
            writeTrace(traceId, requestParam.getSessionId(), userId, "REWRITE",
                    "SUCCESS",
                    elapsedMillis(rewriteStart),
                    "rewrittenQuery=" + shorten(rewrittenQuery, 240));

            long retrieveStart = System.nanoTime();
            List<RagRetrievalService.RetrievedChunk> recalledChunks = ragRetrievalService.retrieve(userDO, rewrittenQuery);
            writeTrace(traceId, requestParam.getSessionId(), userId, "RETRIEVE",
                    "SUCCESS",
                    elapsedMillis(retrieveStart),
                    "candidateCount=" + recalledChunks.size());

            long rerankStart = System.nanoTime();
            List<RagRetrievalService.RetrievedChunk> rerankedChunks = ragRetrievalService.rerank(rewrittenQuery, recalledChunks);
            writeTrace(traceId, requestParam.getSessionId(), userId, "RERANK",
                    "SUCCESS",
                    elapsedMillis(rerankStart),
                    "rerankCount=" + rerankedChunks.size());

            if (rerankedChunks.isEmpty()) {
                answer = "当前知识库中没有该方面的内容，暂时无法回答这个问题。";
                writeTrace(traceId, requestParam.getSessionId(), userId, "GENERATE",
                        "CANCELLED",
                        1L,
                        "rerank-empty");
            } else {
                long generateStart = System.nanoTime();
                answer = chatModelGateway.generateAnswer(
                        requestParam.getMessage(),
                        rewrittenQuery,
                        historyTexts,
                        rerankedChunks);
                writeTrace(traceId, requestParam.getSessionId(), userId, "GENERATE",
                        "SUCCESS",
                        elapsedMillis(generateStart),
                        "citationCount=" + rerankedChunks.size());
                citations = toCitationResponses(rerankedChunks);
                knowledgeHit = true;
            }
        }

        ChatModelInfoRecord modelInfo = chatModelGateway.currentModelInfo();
        ChatMessageDO userMessage = new ChatMessageDO()
                .setSessionId(requestParam.getSessionId())
                .setUserId(userId)
                .setRole("USER")
                .setContent(requestParam.getMessage())
                .setContentType(ChatMessageContentTypeEnum.TEXT.getCode())
                .setSequenceNo(nextSequenceNo)
                .setTraceId(traceId);
        chatMessageMapper.insert(userMessage);

        ChatMessageDO assistantMessage = new ChatMessageDO()
                .setSessionId(requestParam.getSessionId())
                .setUserId(userId)
                .setRole("ASSISTANT")
                .setContent(answer)
                .setContentType(ChatMessageContentTypeEnum.TEXT.getCode())
                .setSequenceNo(nextSequenceNo + 1)
                .setTraceId(traceId)
                .setModelProvider(modelInfo.provider())
                .setModelName(modelInfo.modelName());
        chatMessageMapper.insert(assistantMessage);

        ChatSessionDO updateSession = new ChatSessionDO();
        updateSession.setId(sessionDO.getId());
        updateSession.setLastActiveAt(new Date());
        chatSessionMapper.updateById(updateSession);

        return new ChatResp()
                .setSessionId(requestParam.getSessionId())
                .setUserMessageId(userMessage.getId())
                .setAssistantMessageId(assistantMessage.getId())
                .setTraceId(traceId)
                .setIntentType(intentType)
                .setKnowledgeHit(knowledgeHit)
                .setRewrittenQuery(rewrittenQuery)
                .setAnswer(answer)
                .setCitations(citations);
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
                                if (!closed.get()) {
                                    sendStreamEvent(emitter, each);
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
            List<ChatMessageDO> recentMessages = loadRecentMessages(requestParam.getSessionId());
            int nextSequenceNo = nextSequenceNo(recentMessages);
            List<String> historyTexts = toHistoryTexts(recentMessages);

            long intentStart = System.nanoTime();
            String intentType = chatModelGateway.classifyQuestionIntent(requestParam.getMessage(), historyTexts);
            writeTrace(traceId, requestParam.getSessionId(), userId, "INTENT",
                    "SUCCESS", elapsedMillis(intentStart), "intent=" + intentType);

            boolean deepThinking = Boolean.TRUE.equals(requestParam.getDeepThinking());

            if ("CHITCHAT".equals(intentType)) {
                persistUserMessage(requestParam, userId, traceId, nextSequenceNo);
                if (deepThinking) {
                    return streamThinkingByCandidates(
                            traceId,
                            requestParam.getSessionId(),
                            userId,
                            nextSequenceNo + 1,
                            requestParam.getMessage(),
                            List.of(),
                            chatModelGateway.thinkingCandidateIds(),
                            (candidateId, ignored) -> chatModelGateway.streamThinkingChitchatByCandidate(candidateId, requestParam.getMessage(), historyTexts));
                }
                return streamByCandidates(
                        traceId,
                        requestParam.getSessionId(),
                        userId,
                        nextSequenceNo + 1,
                        requestParam.getMessage(),
                        List.of(),
                        chatModelGateway.streamingCandidateIds(),
                        (candidateId, ignored) -> chatModelGateway.streamChitchatByCandidate(candidateId, requestParam.getMessage(), historyTexts));
            }

            long rewriteStart = System.nanoTime();
            String rewrittenQuery = chatModelGateway.rewriteQuestion(requestParam.getMessage(), historyTexts);
            writeTrace(traceId, requestParam.getSessionId(), userId, "REWRITE",
                    "SUCCESS", elapsedMillis(rewriteStart), "rewrittenQuery=" + shorten(rewrittenQuery, 240));
            long retrieveStart = System.nanoTime();
            List<RagRetrievalService.RetrievedChunk> recalledChunks = ragRetrievalService.retrieve(userDO, rewrittenQuery);
            writeTrace(traceId, requestParam.getSessionId(), userId, "RETRIEVE",
                    "SUCCESS", elapsedMillis(retrieveStart), "candidateCount=" + recalledChunks.size());
            long rerankStart = System.nanoTime();
            List<RagRetrievalService.RetrievedChunk> rerankedChunks = ragRetrievalService.rerank(rewrittenQuery, recalledChunks);
            writeTrace(traceId, requestParam.getSessionId(), userId, "RERANK",
                    "SUCCESS", elapsedMillis(rerankStart), "rerankCount=" + rerankedChunks.size());

            if (rerankedChunks.isEmpty()) {
                persistUserMessage(requestParam, userId, traceId, nextSequenceNo);
                return streamStaticAnswer(
                        traceId,
                        sessionDO,
                        userId,
                        nextSequenceNo + 1,
                        requestParam.getMessage(),
                        "当前知识库中没有该方面的内容，暂时无法回答这个问题。");
            }

            persistUserMessage(requestParam, userId, traceId, nextSequenceNo);
            if (deepThinking) {
                return streamThinkingByCandidates(
                        traceId,
                        requestParam.getSessionId(),
                        userId,
                        nextSequenceNo + 1,
                        requestParam.getMessage(),
                        toStreamCitationResponses(rerankedChunks),
                        chatModelGateway.thinkingCandidateIds(),
                        (candidateId, ignored) -> chatModelGateway.streamThinkingKnowledgeAnswerByCandidate(
                                candidateId,
                                requestParam.getMessage(),
                                rewrittenQuery,
                                historyTexts,
                                rerankedChunks));
            }
            return streamByCandidates(
                    traceId,
                    requestParam.getSessionId(),
                    userId,
                    nextSequenceNo + 1,
                    requestParam.getMessage(),
                    toStreamCitationResponses(rerankedChunks),
                    chatModelGateway.streamingCandidateIds(),
                    (candidateId, ignored) -> chatModelGateway.streamKnowledgeAnswerByCandidate(
                            candidateId,
                            requestParam.getMessage(),
                            rewrittenQuery,
                            historyTexts,
                            rerankedChunks));
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

    private void sendStreamEvent(SseEmitter emitter, ChatStreamEventResp event) {
        try {
            emitter.send(SseEmitter.event()
                    .id(event.getTraceId())
                    .name(event.getEvent())
                    .data(event));
        } catch (Exception ex) {
            throw new IllegalStateException("发送 SSE 事件失败", ex);
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

    private int nextSequenceNo(List<ChatMessageDO> recentMessages) {
        if (recentMessages == null || recentMessages.isEmpty()) {
            return 1;
        }
        ChatMessageDO latest = recentMessages.get(recentMessages.size() - 1);
        return latest.getSequenceNo() == null ? 1 : latest.getSequenceNo() + 1;
    }

    private List<String> toHistoryTexts(List<ChatMessageDO> messages) {
        return messages.stream()
                .map(each -> each.getRole() + ": " + each.getContent())
                .toList();
    }

    private List<ChatCitationResp> toCitationResponses(List<RagRetrievalService.RetrievedChunk> retrievedChunks) {
        return IntStream.range(0, retrievedChunks.size())
                .mapToObj(index -> {
                    RagRetrievalService.RetrievedChunk each = retrievedChunks.get(index);
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

    private List<ChatStreamCitationResp> toStreamCitationResponses(List<RagRetrievalService.RetrievedChunk> retrievedChunks) {
        return IntStream.range(0, retrievedChunks.size())
                .mapToObj(index -> {
                    RagRetrievalService.RetrievedChunk each = retrievedChunks.get(index);
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
            writeTrace(traceId, sessionId, userId, "GENERATE", "FAILED", elapsedMillis(generationStartNanos), "all-candidates-exhausted");
            return Flux.just(ChatStreamEventResp.error(traceId, sessionId,
                    BaseErrorCode.SERVICE_ERROR.code(), "当前没有可用的聊天模型候选"));
        }
        String candidateId = candidateIds.get(index);
        long attemptStart = System.nanoTime();
        if (!chatModelGateway.tryAcquireStreamingCandidate(candidateId)) {
            writeTrace(traceId, sessionId, userId, "STREAM_CANDIDATE",
                    "SKIPPED", elapsedMillis(attemptStart), "candidateId=" + candidateId + ",reason=skipped-circuit-open");
            return streamByCandidate(traceId, sessionId, userId, assistantSequenceNo, question,
                    citations, candidateIds, index + 1, attemptNo, generationStartNanos, streamProvider);
        }

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
                    chatModelGateway.markStreamingCandidateSuccess(candidateId);
                    ChatModelInfoRecord modelInfo = chatModelGateway.candidateInfo(candidateId);
                    ChatMessageDO assistantMessage = persistAssistantMessage(
                            sessionId, userId, assistantSequenceNo, traceId, answerBuilder.toString(), modelInfo);
                    updateSessionLastActiveAt(sessionId);
                    writeTrace(traceId, sessionId, userId, "STREAM_CANDIDATE",
                            "SUCCESS", elapsedMillis(attemptStart), "candidateId=" + candidateId + ",attemptNo=" + attemptNo);
                    Flux<ChatStreamEventResp> citationFlux = citations == null || citations.isEmpty()
                            ? Flux.empty()
                            : Flux.just(ChatStreamEventResp.citation(traceId, sessionId, citations));
                    return citationFlux.concatWithValues(ChatStreamEventResp.messageEnd(traceId, sessionId, assistantMessage.getId()));
                }))
                .onErrorResume(ex -> {
                    chatModelGateway.markStreamingCandidateFailure(candidateId);
                    String reason = resolveStreamFailureReason(ex, sawDelta.get());
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
            writeTrace(traceId, sessionId, userId, "GENERATE", "FAILED", elapsedMillis(generationStartNanos), "all-thinking-candidates-exhausted");
            return Flux.just(ChatStreamEventResp.error(traceId, sessionId,
                    BaseErrorCode.SERVICE_ERROR.code(), "当前没有可用的深度思考模型候选"));
        }
        String candidateId = candidateIds.get(index);
        long attemptStart = System.nanoTime();
        if (!chatModelGateway.tryAcquireStreamingCandidate(candidateId)) {
            writeTrace(traceId, sessionId, userId, "STREAM_CANDIDATE",
                    "SKIPPED", elapsedMillis(attemptStart), "candidateId=" + candidateId + ",reason=skipped-circuit-open");
            return streamThinkingByCandidate(traceId, sessionId, userId, assistantSequenceNo, question,
                    citations, candidateIds, index + 1, attemptNo, generationStartNanos, streamProvider);
        }

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
                    chatModelGateway.markStreamingCandidateSuccess(candidateId);
                    ChatModelInfoRecord modelInfo = chatModelGateway.candidateInfo(candidateId);
                    Long thinkingDurationMs = thinkingStartNanos.get() == null ? null
                            : Math.max(1L, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - thinkingStartNanos.get()));
                    ChatMessageDO assistantMessage = persistAssistantMessage(
                            sessionId, userId, assistantSequenceNo, traceId,
                            answerBuilder.toString(), modelInfo,
                            thinkingBuilder.toString(), thinkingDurationMs);
                    updateSessionLastActiveAt(sessionId);
                    writeTrace(traceId, sessionId, userId, "STREAM_CANDIDATE",
                            "SUCCESS", elapsedMillis(attemptStart), "candidateId=" + candidateId + ",attemptNo=" + attemptNo + ",thinking=" + (thinkingDurationMs != null));
                    Flux<ChatStreamEventResp> citationFlux = citations == null || citations.isEmpty()
                            ? Flux.empty()
                            : Flux.just(ChatStreamEventResp.citation(traceId, sessionId, citations));
                    return citationFlux.concatWithValues(ChatStreamEventResp.messageEnd(traceId, sessionId, assistantMessage.getId()));
                }))
                .onErrorResume(ex -> {
                    chatModelGateway.markStreamingCandidateFailure(candidateId);
                    String reason = resolveStreamFailureReason(ex, sawDelta.get());
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
        QaTraceLogDO traceLogDO = new QaTraceLogDO()
                .setTraceId(traceId)
                .setSessionId(sessionId)
                .setUserId(userId)
                .setStage(stage)
                .setStatus(status)
                .setLatencyMs(latencyMs)
                .setPayloadRef(Boolean.TRUE.equals(traceProperties.getLogPayload()) ? shorten(payload, 512) : null);
        qaTraceLogMapper.insert(traceLogDO);
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
}
