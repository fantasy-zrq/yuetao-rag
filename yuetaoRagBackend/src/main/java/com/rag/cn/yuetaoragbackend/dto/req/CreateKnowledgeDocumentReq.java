package com.rag.cn.yuetaoragbackend.dto.req;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

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

    /**
     * 文档级授权部门ID列表，仅 SENSITIVE 文档需要。
     */
    private List<Long> authorizedDepartmentIds;
}
