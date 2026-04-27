package com.rag.cn.yuetaoragbackend.dto.req;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @author zrq
 * 2026/04/27 10:00
 */
@Data
@Accessors(chain = true)
public class SplitKnowledgeDocumentReq {

    private Long documentId;
}
