package com.rag.cn.yuetaoragbackend.controller;

import com.rag.cn.yuetaoragbackend.dto.req.PageRunsReq;
import com.rag.cn.yuetaoragbackend.dto.resp.PageResp;
import com.rag.cn.yuetaoragbackend.dto.resp.RagTraceDetailResp;
import com.rag.cn.yuetaoragbackend.dto.resp.RagTraceNodeResp;
import com.rag.cn.yuetaoragbackend.dto.resp.RagTraceRunResp;
import com.rag.cn.yuetaoragbackend.framework.convention.Result;
import com.rag.cn.yuetaoragbackend.framework.web.Results;
import com.rag.cn.yuetaoragbackend.service.RagTraceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @author zrq
 * 2026/05/07
 */
@RestController
@RequestMapping("/rag/traces")
@RequiredArgsConstructor
public class RagTraceController {

    private final RagTraceService ragTraceService;

    @GetMapping("/runs")
    public Result<PageResp<RagTraceRunResp>> pageRuns(@RequestBody PageRunsReq requestParam) {
        return Results.success(ragTraceService.pageRuns(requestParam));
    }

    @GetMapping("/runs/{traceId}")
    public Result<RagTraceDetailResp> detail(@PathVariable("traceId") String traceId) {
        return Results.success(ragTraceService.detail(traceId));
    }

    @GetMapping("/runs/{traceId}/nodes")
    public Result<List<RagTraceNodeResp>> nodes(@PathVariable("traceId") String traceId) {
        return Results.success(ragTraceService.nodes(traceId));
    }
}
