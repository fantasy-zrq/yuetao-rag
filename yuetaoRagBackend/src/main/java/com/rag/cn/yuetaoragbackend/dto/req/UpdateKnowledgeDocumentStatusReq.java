package com.rag.cn.yuetaoragbackend.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @author zrq
 * 2026/05/07
 */
@Data
@Accessors(chain = true)
public class UpdateKnowledgeDocumentStatusReq {

    @NotNull(message = "文档ID不能为空")
    private Long id;

    @NotBlank(message = "文档状态不能为空")
    private String status;
}
