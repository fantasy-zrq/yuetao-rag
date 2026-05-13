package com.rag.cn.yuetaoragbackend.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class StopChatStreamReq {

    @NotNull(message = "会话ID不能为空")
    private Long sessionId;

    @NotBlank(message = "流式追踪ID不能为空")
    private String traceId;
}
