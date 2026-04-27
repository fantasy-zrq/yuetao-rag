package com.rag.cn.yuetaoragbackend.dto.req;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @author zrq
 * 2026/04/26 17:00
 */
@Data
@Accessors(chain = true)
public class DeleteKnowledgeDocumentReq {

    private Long id;
}
