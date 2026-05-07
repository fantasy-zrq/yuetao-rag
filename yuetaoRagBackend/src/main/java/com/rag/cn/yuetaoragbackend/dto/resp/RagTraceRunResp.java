package com.rag.cn.yuetaoragbackend.dto.resp;

import java.util.Date;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @author zrq
 * 2026/05/07
 */
@Data
@Accessors(chain = true)
public class RagTraceRunResp {

    private String traceId;

    private String traceName;

    private Long conversationId;

    private Long taskId;

    private Long userId;

    private String username;

    private String status;

    private Long durationMs;

    private Date startTime;

    private Date endTime;
}
