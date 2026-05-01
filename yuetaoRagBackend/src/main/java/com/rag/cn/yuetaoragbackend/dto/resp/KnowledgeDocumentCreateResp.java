package com.rag.cn.yuetaoragbackend.dto.resp;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

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

    /** 分块模式。 */
    private String chunkMode;

    /** 分块配置 JSON。 */
    private String chunkConfig;

    /** 文档状态。 */
    private String status;

    /** RustFS 访问 URL。 */
    private String storageUrl;

    /** 文档级授权部门ID列表。 */
    private List<Long> authorizedDepartmentIds;
}
