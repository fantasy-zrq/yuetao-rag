package com.rag.cn.yuetaoragbackend.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * @author zrq
 * 2026/04/22 14:40
 */
@Data
@TableName("t_knowledge_document")
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class KnowledgeDocumentDO extends BaseDO {

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

    /** 文件大小，单位字节。 */
    private Long fileSize;

    /** 文档解析状态。 */
    private String parseStatus;

    /** 分块模式。 */
    private String chunkMode;

    /** 分块配置 JSON。 */
    private String chunkConfig;

    /** 文档可见性范围标签。 */
    private String visibilityScope;

    /** 最低可访问职级。 */
    private Integer minRankLevel;

    /** 文档状态。 */
    private String status;

    /** 创建人用户ID。 */
    private Long createdBy;

    /** 更新人用户ID。 */
    private Long updatedBy;
}
