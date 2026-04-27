package com.rag.cn.yuetaoragbackend.dto.req;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @author zrq
 * 2026/04/22 15:05
 */
@Data
@Accessors(chain = true)
public class CreateKnowledgeDocumentReq {

    /**
     * 所属知识库ID。
     */
    private Long knowledgeBaseId;

    /**
     * 分块模式。
     */
    private String chunkMode;

    /**
     * 分块配置 JSON。
     */
    private String chunkConfig;

    /**
     * 文档可见性范围。
     */
    private String visibilityScope;

    /**
     * 最低可访问职级。
     */
    private Integer minRankLevel;
}
