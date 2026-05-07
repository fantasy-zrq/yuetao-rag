package com.rag.cn.yuetaoragbackend.controller;

import com.rag.cn.yuetaoragbackend.dto.req.CreateKnowledgeDocumentReq;
import com.rag.cn.yuetaoragbackend.dto.req.DeleteKnowledgeDocumentReq;
import com.rag.cn.yuetaoragbackend.dto.req.SplitKnowledgeDocumentReq;
import com.rag.cn.yuetaoragbackend.dto.req.UpdateKnowledgeDocumentReq;
import com.rag.cn.yuetaoragbackend.dto.req.UpdateKnowledgeDocumentStatusReq;
import com.rag.cn.yuetaoragbackend.dto.resp.KnowledgeDocumentChunkLogResp;
import com.rag.cn.yuetaoragbackend.dto.resp.KnowledgeDocumentCreateResp;
import com.rag.cn.yuetaoragbackend.dto.resp.KnowledgeDocumentDetailResp;
import com.rag.cn.yuetaoragbackend.dto.resp.KnowledgeDocumentListResp;
import com.rag.cn.yuetaoragbackend.framework.convention.Result;
import com.rag.cn.yuetaoragbackend.framework.web.Results;
import com.rag.cn.yuetaoragbackend.service.KnowledgeDocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * @author zrq
 * 2026/04/22 15:20
 */
@RestController
@RequestMapping("/knowledge-documents")
@RequiredArgsConstructor
public class KnowledgeDocumentController {

    private final KnowledgeDocumentService knowledgeDocumentService;

    @PostMapping("/create")
    public Result<KnowledgeDocumentCreateResp> createKnowledgeDocument(@RequestPart("file") MultipartFile file,
                                                                       @ModelAttribute CreateKnowledgeDocumentReq requestParam) {
        return Results.success(knowledgeDocumentService.createKnowledgeDocument(file, requestParam));
    }

    @PostMapping("/update")
    public Result<KnowledgeDocumentDetailResp> updateKnowledgeDocument(@RequestBody UpdateKnowledgeDocumentReq requestParam) {
        return Results.success(knowledgeDocumentService.updateKnowledgeDocument(requestParam));
    }

    @PostMapping("/delete")
    public Result<Void> deleteKnowledgeDocument(@RequestBody DeleteKnowledgeDocumentReq requestParam) {
        knowledgeDocumentService.deleteKnowledgeDocument(requestParam);
        return Results.success();
    }

    @PostMapping("/split")
    public Result<Void> splitKnowledgeDocument(@RequestBody SplitKnowledgeDocumentReq requestParam) {
        knowledgeDocumentService.splitKnowledgeDocument(requestParam);
        return Results.success();
    }

    @PostMapping("/status")
    public Result<KnowledgeDocumentDetailResp> updateKnowledgeDocumentStatus(@RequestBody UpdateKnowledgeDocumentStatusReq requestParam) {
        return Results.success(knowledgeDocumentService.updateKnowledgeDocumentStatus(requestParam));
    }

    @GetMapping("/list")
    public Result<List<KnowledgeDocumentListResp>> listKnowledgeDocuments(@RequestParam("knowledgeBaseId") Long knowledgeBaseId) {
        return Results.success(knowledgeDocumentService.listByKnowledgeBaseId(knowledgeBaseId));
    }

    @GetMapping("/{id}/chunk-logs")
    public Result<List<KnowledgeDocumentChunkLogResp>> listChunkLogs(@PathVariable("id") Long id) {
        return Results.success(knowledgeDocumentService.listChunkLogs(id));
    }

    @GetMapping("/detail/{id}")
    public Result<KnowledgeDocumentDetailResp> getKnowledgeDocument(@PathVariable("id") Long id) {
        return Results.success(knowledgeDocumentService.getKnowledgeDocument(id));
    }
}
