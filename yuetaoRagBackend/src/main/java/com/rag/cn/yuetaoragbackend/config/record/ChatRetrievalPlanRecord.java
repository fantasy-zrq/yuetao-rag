package com.rag.cn.yuetaoragbackend.config.record;

import com.rag.cn.yuetaoragbackend.dao.projection.RetrievedChunk;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 检索与重排序完成后的结果，供同步和流式回答链路共享。
 */
public record ChatRetrievalPlanRecord(List<RetrievedChunk> rerankedChunks, String promptSnippet,
                                      String promptTemplate, boolean refusal, String rewrittenQuery) {

    public ChatRetrievalPlanRecord {
        rerankedChunks = rerankedChunks == null ? List.of() : List.copyOf(rerankedChunks);
    }

    public static ChatRetrievalPlanRecord noRetrieval() {
        return new ChatRetrievalPlanRecord(List.of(), null, null, false, null);
    }

    public static ChatRetrievalPlanRecord success(List<RetrievedChunk> rerankedChunks, String promptSnippet,
                                                  String promptTemplate, String rewrittenQuery) {
        return new ChatRetrievalPlanRecord(rerankedChunks, promptSnippet, promptTemplate, false, rewrittenQuery);
    }

    public static ChatRetrievalPlanRecord refusalResult() {
        return new ChatRetrievalPlanRecord(List.of(), null, null, true, null);
    }

    public String rewrittenQueryOr(String fallback) {
        return StringUtils.hasText(rewrittenQuery) ? rewrittenQuery : fallback;
    }
}
