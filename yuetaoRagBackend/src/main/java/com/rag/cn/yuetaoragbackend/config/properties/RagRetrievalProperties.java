package com.rag.cn.yuetaoragbackend.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author zrq
 * 2026/04/22 12:00
 */
@Data
@ConfigurationProperties(prefix = "app.rag.retrieval")
public class RagRetrievalProperties {

    /**
     * 向量检索最终返回的 topK 数量。
     */
    private Integer topK;

    /**
     * 候选召回放大倍数。
     */
    private Integer candidateMultiplier;

    /**
     * 检索失败时是否启用全局兜底。
     */
    private Boolean enableGlobalFallback;

    /**
     * 单个切片允许的最大 token 数。
     */
    private Integer chunkMaxTokens;
}
