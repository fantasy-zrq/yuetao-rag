package com.rag.cn.yuetaoragbackend.dto.resp;

import java.util.Date;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @author zrq
 * 2026/04/22 16:00
 */
@Data
@Accessors(chain = true)
public class ChatSessionListResp {

    /** 会话ID。 */
    private Long id;

    /** 会话标题。 */
    private String title;

    /** 会话状态。 */
    private String status;

    /** 最近活跃时间。 */
    private Date lastActiveAt;
}
