package com.rag.cn.yuetaoragbackend.dto.resp;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @author zrq
 * 2026/04/22 16:00
 */
@Data
@Accessors(chain = true)
public class KnowledgeDocumentCreateResp {

    /** 文档ID。 */
    private Long id;

    /** 所属知识库ID。 */
    private Long knowledgeBaseId;

    /** 文档标题。 */
    private String title;

    /** 文档解析状态。 */
    private String parseStatus;

    /** 文档状态。 */
    private String status;

    /** RustFS 访问 URL。 */
    private String storageUrl;
}
