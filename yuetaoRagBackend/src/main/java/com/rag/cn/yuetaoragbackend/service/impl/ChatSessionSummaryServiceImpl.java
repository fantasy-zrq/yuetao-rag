package com.rag.cn.yuetaoragbackend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.rag.cn.yuetaoragbackend.config.enums.DeleteFlagEnum;
import com.rag.cn.yuetaoragbackend.config.properties.MemoryProperties;
import com.rag.cn.yuetaoragbackend.dao.entity.ChatMessageDO;
import com.rag.cn.yuetaoragbackend.dao.entity.ChatSessionSummaryDO;
import com.rag.cn.yuetaoragbackend.dao.mapper.ChatMessageMapper;
import com.rag.cn.yuetaoragbackend.dao.mapper.ChatSessionSummaryMapper;
import com.rag.cn.yuetaoragbackend.service.ChatSessionSummaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author zrq
 * 2026/05/11
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatSessionSummaryServiceImpl implements ChatSessionSummaryService {

    private static final int SUMMARY_MAX_CHARS = 500;

    private final ChatSessionSummaryMapper chatSessionSummaryMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final ChatModelGateway chatModelGateway;
    private final MemoryProperties memoryProperties;

    @Override
    public void maybeSummarize(Long sessionId, int latestSequenceNo) {
        try {
            doMaybeSummarize(sessionId, latestSequenceNo);
        } catch (Exception ex) {
            log.warn("会话摘要生成失败, sessionId={}", sessionId, ex);
        }
    }

    @Override
    public ChatSessionSummaryDO loadLatestSummary(Long sessionId) {
        return chatSessionSummaryMapper.selectOne(Wrappers.<ChatSessionSummaryDO>lambdaQuery()
                .eq(ChatSessionSummaryDO::getSessionId, sessionId)
                .eq(ChatSessionSummaryDO::getDeleteFlag, DeleteFlagEnum.NORMAL.getCode())
                .orderByDesc(ChatSessionSummaryDO::getSummaryVersion)
                .last("LIMIT 1"));
    }

    private void doMaybeSummarize(Long sessionId, int latestSequenceNo) {
        int threshold = memoryProperties.getSummaryTriggerThreshold() == null
                || memoryProperties.getSummaryTriggerThreshold() <= 0
                ? 40 : memoryProperties.getSummaryTriggerThreshold();
        int recentWindowSize = memoryProperties.getRecentWindowSize() == null
                || memoryProperties.getRecentWindowSize() <= 0
                ? 12 : memoryProperties.getRecentWindowSize();

        ChatSessionSummaryDO existingSummary = loadLatestSummary(sessionId);
        int sourceSeq = existingSummary == null ? 0 : existingSummary.getSourceMessageSeq();

        int messagesSinceSummary = latestSequenceNo - sourceSeq;
        if (messagesSinceSummary < threshold) {
            return;
        }

        // 需要摘要的消息范围：(sourceSeq, latestSequenceNo - recentWindowSize]
        int upperBound = latestSequenceNo - recentWindowSize;
        if (upperBound <= sourceSeq) {
            return;
        }

        List<ChatMessageDO> messagesToSummarize = chatMessageMapper.selectList(
                Wrappers.<ChatMessageDO>lambdaQuery()
                        .eq(ChatMessageDO::getDeleteFlag, DeleteFlagEnum.NORMAL.getCode())
                        .eq(ChatMessageDO::getSessionId, sessionId)
                        .gt(ChatMessageDO::getSequenceNo, sourceSeq)
                        .le(ChatMessageDO::getSequenceNo, upperBound)
                        .orderByAsc(ChatMessageDO::getSequenceNo));

        if (messagesToSummarize.isEmpty()) {
            return;
        }

        List<String> historyTexts = messagesToSummarize.stream()
                .map(each -> each.getRole() + ": " + each.getContent())
                .toList();

        log.info("开始生成会话摘要, sessionId={}, messageCount={}, range=({}, {}]",
                sessionId, messagesToSummarize.size(), sourceSeq, upperBound);

        String summaryText = chatModelGateway.generateSummary(historyTexts, SUMMARY_MAX_CHARS);

        int nextVersion = existingSummary == null ? 1 : existingSummary.getSummaryVersion() + 1;
        ChatSessionSummaryDO newSummary = new ChatSessionSummaryDO()
                .setSessionId(sessionId)
                .setSummaryText(summaryText)
                .setSummaryVersion(nextVersion)
                .setSourceMessageSeq(upperBound);
        chatSessionSummaryMapper.insert(newSummary);

        log.info("会话摘要生成完成, sessionId={}, version={}, sourceMessageSeq={}",
                sessionId, nextVersion, upperBound);
    }
}
