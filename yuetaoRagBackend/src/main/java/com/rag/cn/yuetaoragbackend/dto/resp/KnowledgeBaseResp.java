package com.rag.cn.yuetaoragbackend.dto.resp;

import java.util.Date;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @author zrq
 * 2026/04/22 15:05
 */
@Data
@Accessors(chain = true)
public class KnowledgeBaseResp {

    /** 知识库ID。 */
    private Long id;

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

    /** 创建时间。 */
    private Date createTime;

    /** 更新时间。 */
    private Date updateTime;
}
