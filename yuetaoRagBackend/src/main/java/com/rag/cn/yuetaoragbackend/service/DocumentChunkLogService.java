package com.rag.cn.yuetaoragbackend.service;

/**
 * @author zrq
 * 2026/04/28 22:00
 */
public interface DocumentChunkLogService {

    void recordSplitResult(Long chunkLogId, int chunkCount, long splitCostMillis);

    void recordVectorResult(Long chunkLogId, long vectorCostMillis);

    void markSuccess(Long chunkLogId, int chunkCount, long splitCostMillis, long vectorCostMillis, long totalCostMillis);

    void markFailed(Long chunkLogId, String errorMessage);

    void markTimeout(Long documentId);
}
