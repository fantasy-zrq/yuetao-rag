package com.rag.cn.yuetaoragbackend.dto.req;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @author zrq
 * 2026/05/07
 */
@Data
@Accessors(chain = true)
public class PageRunsReq {

    private Long current = 1L;

    private Long size = 10L;

    private String traceId;
}
