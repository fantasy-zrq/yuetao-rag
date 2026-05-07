package com.rag.cn.yuetaoragbackend.dto.resp;

import java.util.List;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @author zrq
 * 2026/05/07
 */
@Data
@Accessors(chain = true)
public class RagTraceDetailResp {

    private RagTraceRunResp run;

    private List<RagTraceNodeResp> nodes;
}
