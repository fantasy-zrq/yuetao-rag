package com.rag.cn.yuetaoragbackend.dto.resp;

import java.util.Date;
import java.util.List;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @author zrq
 * 2026/04/22 16:00
 */
@Data
@Accessors(chain = true)
public class KnowledgeDocumentDetailResp {

    /** 文档ID。 */
    private Long id;

    /** 所属知识库ID。 */
    private Long knowledgeBaseId;

    /** 文档标题。 */
    private String title;

    /** 文档来源类型。 */
    private String sourceType;

    /** 文件 MIME 类型。 */
    private String mimeType;

    /** RustFS bucket 名称。 */
    private String storageBucket;

    /** RustFS 对象键。 */
    private String storageKey;

    /** 对象存储 ETag。 */
    private String storageEtag;

    /** RustFS 访问 URL。 */
    private String storageUrl;

    /** 文件大小。 */
    private Long fileSize;

    /** 文档解析状态。 */
    private String parseStatus;

    /** 文档解析失败原因。 */
    private String failReason;

    /** 分块模式。 */
    private String chunkMode;

    /** 分块配置 JSON。 */
    private String chunkConfig;

    /** 文档可见性范围。 */
    private String visibilityScope;

    /** 最低可访问职级。 */
    private Integer minRankLevel;

    /** 文档状态。 */
    private String status;

    /** 当前分块数量。 */
    private Integer chunkCount;

    /** 创建时间。 */
    private Date createTime;

    /** 更新时间。 */
    private Date updateTime;

    /** 文档级授权部门ID列表。 */
    private List<Long> authorizedDepartmentIds;
}
