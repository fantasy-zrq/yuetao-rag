package com.rag.cn.yuetaoragbackend.dto.req;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @author zrq
 * 2026/04/22 15:05
 */
@Data
@Accessors(chain = true)
public class CreateChatSessionReq {

    /** 会话标题。 */
    private String title;

    /** 会话状态。 */
    private String status;
}
