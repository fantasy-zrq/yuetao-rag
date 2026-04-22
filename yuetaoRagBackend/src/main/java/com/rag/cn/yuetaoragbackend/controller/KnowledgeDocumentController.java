package com.rag.cn.yuetaoragbackend.controller;

import com.rag.cn.yuetaoragbackend.dao.entity.KnowledgeDocumentDO;
import com.rag.cn.yuetaoragbackend.dto.req.CreateKnowledgeDocumentReq;
import com.rag.cn.yuetaoragbackend.dto.resp.KnowledgeDocumentResp;
import com.rag.cn.yuetaoragbackend.framework.convention.Result;
import com.rag.cn.yuetaoragbackend.framework.web.Results;
import com.rag.cn.yuetaoragbackend.service.KnowledgeDocumentService;
import java.util.List;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author zrq
 * 2026/04/22 15:20
 */
@RestController
@RequestMapping("/knowledge-documents")
public class KnowledgeDocumentController {

    private final KnowledgeDocumentService knowledgeDocumentService;

    public KnowledgeDocumentController(KnowledgeDocumentService knowledgeDocumentService) {
        this.knowledgeDocumentService = knowledgeDocumentService;
    }

    @PostMapping
    public Result<KnowledgeDocumentResp> createKnowledgeDocument(@RequestBody CreateKnowledgeDocumentReq requestParam) {
        return Results.success(toKnowledgeDocumentResp(knowledgeDocumentService.createKnowledgeDocument(requestParam)));
    }

    @GetMapping
    public Result<List<KnowledgeDocumentResp>> listKnowledgeDocuments(@RequestParam("knowledgeBaseId") Long knowledgeBaseId) {
        return Results.success(knowledgeDocumentService.listByKnowledgeBaseId(knowledgeBaseId).stream()
            .map(this::toKnowledgeDocumentResp)
            .toList());
    }

    @GetMapping("/{id}")
    public Result<KnowledgeDocumentResp> getKnowledgeDocument(@PathVariable("id") Long id) {
        return Results.success(toKnowledgeDocumentResp(knowledgeDocumentService.getKnowledgeDocument(id)));
    }

    private KnowledgeDocumentResp toKnowledgeDocumentResp(KnowledgeDocumentDO knowledgeDocumentDO) {
        if (knowledgeDocumentDO == null) {
            return null;
        }
        KnowledgeDocumentResp response = new KnowledgeDocumentResp();
        BeanUtils.copyProperties(knowledgeDocumentDO, response);
        return response;
    }
}
