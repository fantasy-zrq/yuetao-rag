package com.rag.cn.yuetaoragbackend.dto.req;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @author zrq
 * 2026/04/26 10:10
 */
@Data
@Accessors(chain = true)
public class DeleteKnowledgeBaseReq {

    /** 知识库ID。 */
    @NotNull(message = "知识库ID不能为空")
    private Long id;
}
