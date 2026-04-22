package com.rag.cn.yuetaoragbackend.dto.req;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @author zrq
 * 2026/04/22 15:05
 */
@Data
@Accessors(chain = true)
public class CreateKnowledgeBaseReq {

    /** 知识库名称。 */
    private String name;

    /** 知识库描述。 */
    private String description;

    /** 知识库状态。 */
    private String status;

    /** 默认向量模型名称。 */
    private String embeddingModel;

    /** RustFS bucket 名称。 */
    private String collectionName;
}
