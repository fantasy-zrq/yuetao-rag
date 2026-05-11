package com.rag.cn.yuetaoragbackend.dto.req;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @author zrq
 * 2026/04/27 10:00
 */
@Data
@Accessors(chain = true)
public class SplitKnowledgeDocumentReq {

    @NotNull(message = "文档ID不能为空")
    private Long documentId;
}
