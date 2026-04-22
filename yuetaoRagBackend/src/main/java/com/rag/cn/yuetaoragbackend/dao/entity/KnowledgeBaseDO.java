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
@TableName("t_knowledge_base")
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class KnowledgeBaseDO extends BaseDO {

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

    /** 创建人用户ID。 */
    private Long createdBy;

    /** 更新人用户ID。 */
    private Long updatedBy;
}
