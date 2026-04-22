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
@TableName("t_chunk")
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class ChunkDO extends BaseDO {

    /** 所属知识库ID。 */
    private Long knowledgeBaseId;

    /** 所属文档ID。 */
    private Long documentId;

    /** 切片顺序号。 */
    private Integer chunkNo;

    /** 切片哈希值。 */
    private String chunkHash;

    /** 原始切片内容。 */
    private String originalContent;

    /** 编辑后的切片内容。 */
    private String editedContent;

    /** 最终生效的切片内容。 */
    private String effectiveContent;

    /** 切片估算 token 数。 */
    private Integer tokenCount;

    /** 向量化状态。 */
    private String embeddingStatus;

    /** 是否启用。 */
    private Boolean enabled;

    /** 是否人工编辑。 */
    private Boolean manualEdited;

    /** 创建人用户ID。 */
    private Long createdBy;

    /** 更新人用户ID。 */
    private Long updatedBy;
}
