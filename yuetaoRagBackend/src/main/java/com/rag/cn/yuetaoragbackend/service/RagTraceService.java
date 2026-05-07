package com.rag.cn.yuetaoragbackend.service;

import com.rag.cn.yuetaoragbackend.dto.req.PageRunsReq;
import com.rag.cn.yuetaoragbackend.dto.resp.PageResp;
import com.rag.cn.yuetaoragbackend.dto.resp.RagTraceDetailResp;
import com.rag.cn.yuetaoragbackend.dto.resp.RagTraceNodeResp;
import com.rag.cn.yuetaoragbackend.dto.resp.RagTraceRunResp;
import java.util.List;

/**
 * @author zrq
 * 2026/05/07
 */
public interface RagTraceService {

    PageResp<RagTraceRunResp> pageRuns(PageRunsReq requestParam);

    RagTraceDetailResp detail(String traceId);

    List<RagTraceNodeResp> nodes(String traceId);
}
