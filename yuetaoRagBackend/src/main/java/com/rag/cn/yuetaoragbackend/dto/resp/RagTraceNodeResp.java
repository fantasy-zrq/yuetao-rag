package com.rag.cn.yuetaoragbackend.dto.resp;

import java.util.Date;
import java.util.Map;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @author zrq
 * 2026/05/07
 */
@Data
@Accessors(chain = true)
public class RagTraceNodeResp {

    private String traceId;

    private String nodeId;

    private String parentNodeId;

    private Integer depth;

    private String nodeType;

    private String nodeName;

    private String status;

    private String errorMessage;

    private String payloadRef;

    private Map<String, Object> details;

    private Long durationMs;

    private Date startTime;

    private Date endTime;
}
