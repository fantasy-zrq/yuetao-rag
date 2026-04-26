package com.rag.cn.yuetaoragbackend.dto.req;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @author zrq
 * 2026/04/26 10:10
 */
@Data
@Accessors(chain = true)
public class UpdateKnowledgeBaseReq {

    /** 知识库ID。 */
    private Long id;

    /** 知识库名称。 */
    private String name;
}
