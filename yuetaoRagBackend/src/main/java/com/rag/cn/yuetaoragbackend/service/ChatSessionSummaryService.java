package com.rag.cn.yuetaoragbackend.service;

import com.rag.cn.yuetaoragbackend.dao.entity.ChatSessionSummaryDO;

/**
 * @author zrq
 * 2026/05/11
 */
public interface ChatSessionSummaryService {

    /**
     * 检查会话是否需要生成摘要，若需要则异步生成并存储。
     *
     * @param sessionId       会话ID
     * @param latestSequenceNo 当前最新消息序列号
     */
    void maybeSummarize(Long sessionId, int latestSequenceNo);

    /**
     * 加载会话的最新摘要。
     *
     * @param sessionId 会话ID
     * @return 最新摘要，无摘要时返回 null
     */
    ChatSessionSummaryDO loadLatestSummary(Long sessionId);
}
