package com.rag.cn.yuetaoragbackend.dto.req;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @author zrq
 * 2026/05/09
 */
@Data
@Accessors(chain = true)
public class DeleteChatSessionReq {

    @NotNull(message = "会话ID不能为空")
    private Long id;
}
