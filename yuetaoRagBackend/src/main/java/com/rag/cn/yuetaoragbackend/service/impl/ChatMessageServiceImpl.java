package com.rag.cn.yuetaoragbackend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.rag.cn.yuetaoragbackend.config.enums.ChatMessageContentTypeEnum;
import com.rag.cn.yuetaoragbackend.config.enums.ChatSessionStatusEnum;
import com.rag.cn.yuetaoragbackend.config.enums.DeleteFlagEnum;
import com.rag.cn.yuetaoragbackend.config.properties.MemoryProperties;
import com.rag.cn.yuetaoragbackend.config.properties.TraceProperties;
import com.rag.cn.yuetaoragbackend.dao.entity.ChatMessageDO;
import com.rag.cn.yuetaoragbackend.dao.entity.ChatSessionDO;
import com.rag.cn.yuetaoragbackend.dao.entity.QaTraceLogDO;
import com.rag.cn.yuetaoragbackend.dao.entity.UserDO;
import com.rag.cn.yuetaoragbackend.dao.mapper.ChatMessageMapper;
import com.rag.cn.yuetaoragbackend.dao.mapper.ChatSessionMapper;
import com.rag.cn.yuetaoragbackend.dao.mapper.QaTraceLogMapper;
import com.rag.cn.yuetaoragbackend.dao.mapper.UserMapper;
import com.rag.cn.yuetaoragbackend.dto.req.ChatReq;
import com.rag.cn.yuetaoragbackend.dto.req.CreateChatMessageReq;
import com.rag.cn.yuetaoragbackend.dto.resp.*;
import com.rag.cn.yuetaoragbackend.framework.exception.ClientException;
import com.rag.cn.yuetaoragbackend.service.ChatMessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

/**
 * @author zrq
 * 2026/04/22 15:10
 */
@Service
@RequiredArgsConstructor
public class ChatMessageServiceImpl extends ServiceImpl<ChatMessageMapper, ChatMessageDO>
        implements ChatMessageService {

    private final ChatMessageMapper chatMessageMapper;
    private final ChatSessionMapper chatSessionMapper;
    private final UserMapper userMapper;
    private final QaTraceLogMapper qaTraceLogMapper;
    private final ChatModelGateway chatModelGateway;
    private final RagRetrievalService ragRetrievalService;
    private final MemoryProperties memoryProperties;
    private final TraceProperties traceProperties;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChatResp chat(ChatReq requestParam) {
        if (requestParam == null || requestParam.getSessionId() == null || requestParam.getUserId() == null
                || !StringUtils.hasText(requestParam.getMessage())) {
            throw new ClientException("会话ID、用户ID和消息内容不能为空");
        }

        ChatSessionDO sessionDO = requireSession(requestParam.getSessionId(), requestParam.getUserId());
        UserDO userDO = requireUser(requestParam.getUserId());

        String traceId = StringUtils.hasText(requestParam.getTraceId()) ? requestParam.getTraceId() : UUID.randomUUID().toString();

        List<ChatMessageDO> recentMessages = loadRecentMessages(requestParam.getSessionId());
        int nextSequenceNo = nextSequenceNo(recentMessages);
        List<String> historyTexts = toHistoryTexts(recentMessages);

        long intentStart = System.nanoTime();
        String intentType = chatModelGateway.classifyQuestionIntent(requestParam.getMessage(), historyTexts);
        writeTrace(traceId, requestParam.getSessionId(), requestParam.getUserId(), "INTENT",
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
            writeTrace(traceId, requestParam.getSessionId(), requestParam.getUserId(), "GENERATE",
                    "SUCCESS",
                    elapsedMillis(generateStart),
                    "intent=CHITCHAT");
        } else {
            long rewriteStart = System.nanoTime();
            rewrittenQuery = chatModelGateway.rewriteQuestion(requestParam.getMessage(), historyTexts);
            writeTrace(traceId, requestParam.getSessionId(), requestParam.getUserId(), "REWRITE",
                    "SUCCESS",
                    elapsedMillis(rewriteStart),
                    "rewrittenQuery=" + shorten(rewrittenQuery, 240));

            long retrieveStart = System.nanoTime();
            List<RagRetrievalService.RetrievedChunk> recalledChunks = ragRetrievalService.retrieve(userDO, rewrittenQuery);
            writeTrace(traceId, requestParam.getSessionId(), requestParam.getUserId(), "RETRIEVE",
                    "SUCCESS",
                    elapsedMillis(retrieveStart),
                    "candidateCount=" + recalledChunks.size());

            long rerankStart = System.nanoTime();
            List<RagRetrievalService.RetrievedChunk> rerankedChunks = ragRetrievalService.rerank(rewrittenQuery, recalledChunks);
            writeTrace(traceId, requestParam.getSessionId(), requestParam.getUserId(), "RERANK",
                    "SUCCESS",
                    elapsedMillis(rerankStart),
                    "rerankCount=" + rerankedChunks.size());

            if (rerankedChunks.isEmpty()) {
                answer = "当前知识库中没有该方面的内容，暂时无法回答这个问题。";
                writeTrace(traceId, requestParam.getSessionId(), requestParam.getUserId(), "GENERATE",
                        "CANCELLED",
                        0L,
                        "rerank-empty");
            } else {
                long generateStart = System.nanoTime();
                answer = chatModelGateway.generateAnswer(
                        requestParam.getMessage(),
                        rewrittenQuery,
                        historyTexts,
                        rerankedChunks);
                writeTrace(traceId, requestParam.getSessionId(), requestParam.getUserId(), "GENERATE",
                        "SUCCESS",
                        elapsedMillis(generateStart),
                        "citationCount=" + rerankedChunks.size());
                citations = toCitationResponses(rerankedChunks);
                knowledgeHit = true;
            }
        }

        ChatModelGateway.ModelInfo modelInfo = chatModelGateway.currentModelInfo();
        ChatMessageDO userMessage = new ChatMessageDO()
                .setSessionId(requestParam.getSessionId())
                .setUserId(requestParam.getUserId())
                .setRole("USER")
                .setContent(requestParam.getMessage())
                .setContentType(ChatMessageContentTypeEnum.TEXT.getCode())
                .setSequenceNo(nextSequenceNo)
                .setTraceId(traceId);
        chatMessageMapper.insert(userMessage);

        ChatMessageDO assistantMessage = new ChatMessageDO()
                .setSessionId(requestParam.getSessionId())
                .setUserId(requestParam.getUserId())
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
    public ChatMessageCreateResp createChatMessage(CreateChatMessageReq requestParam) {
        ChatMessageDO messageDO = new ChatMessageDO()
                .setSessionId(requestParam.getSessionId())
                .setUserId(requestParam.getUserId())
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
        ChatMessageDO messageDO = chatMessageMapper.selectById(id);
        if (messageDO == null) {
            return null;
        }
        ChatMessageDetailResp response = new ChatMessageDetailResp();
        BeanUtils.copyProperties(messageDO, response);
        return response;
    }

    private ChatSessionDO requireSession(Long sessionId, Long userId) {
        ChatSessionDO sessionDO = chatSessionMapper.selectById(sessionId);
        if (sessionDO == null || !DeleteFlagEnum.NORMAL.getCode().equals(sessionDO.getDeleteFlag())) {
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

    private UserDO requireUser(Long userId) {
        UserDO userDO = userMapper.selectById(userId);
        if (userDO == null || !DeleteFlagEnum.NORMAL.getCode().equals(userDO.getDeleteFlag())) {
            throw new ClientException("用户不存在");
        }
        return userDO;
    }

    private List<ChatMessageDO> loadRecentMessages(Long sessionId) {
        int limit = memoryProperties.getRecentWindowSize() == null || memoryProperties.getRecentWindowSize() <= 0
                ? 12 : memoryProperties.getRecentWindowSize();
        LambdaQueryWrapper<ChatMessageDO> queryWrapper = Wrappers.<ChatMessageDO>lambdaQuery()
                .eq(ChatMessageDO::getDeleteFlag, DeleteFlagEnum.NORMAL.getCode())
                .eq(ChatMessageDO::getSessionId, sessionId)
                .orderByDesc(ChatMessageDO::getSequenceNo)
                .last("limit " + limit);
        List<ChatMessageDO> recentMessages = chatMessageMapper.selectList(queryWrapper);
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
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }

    private String shorten(String text, int maxLength) {
        if (!StringUtils.hasText(text) || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }
}
