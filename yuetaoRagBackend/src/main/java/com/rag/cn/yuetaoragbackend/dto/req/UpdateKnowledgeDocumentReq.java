package com.rag.cn.yuetaoragbackend.dto.req;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * @author zrq
 * 2026/04/26 17:00
 */
@Data
@Accessors(chain = true)
public class UpdateKnowledgeDocumentReq {

    private Long id;

    private String title;

    private String chunkMode;

    private String chunkConfig;

    private String visibilityScope;

    private Integer minRankLevel;

    private List<Long> authorizedDepartmentIds;
}
