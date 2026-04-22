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
@TableName("t_chunk_vector")
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class ChunkVectorDO extends BaseDO {

    /** 所属知识库ID。 */
    private Long knowledgeBaseId;

    /** 所属文档ID。 */
    private Long documentId;

    /** 所属切片ID。 */
    private Long chunkId;

    /** 向量模型名称。 */
    private String embeddingModel;

    /** 向量模型提供商。 */
    private String embeddingProvider;

    /** 向量维度。 */
    private Integer vectorDimension;

    /** 向量数据。 */
    private String embedding;

    /** 向量对应的文本快照。 */
    private String contentSnapshot;

    /** 是否启用。 */
    private Boolean enabled;
}
