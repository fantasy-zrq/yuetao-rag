package com.rag.cn.yuetaoragbackend.controller;

import com.rag.cn.yuetaoragbackend.dto.req.CreateKnowledgeBaseReq;
import com.rag.cn.yuetaoragbackend.dto.resp.KnowledgeBaseCreateResp;
import com.rag.cn.yuetaoragbackend.dto.resp.KnowledgeBaseDetailResp;
import com.rag.cn.yuetaoragbackend.dto.resp.KnowledgeBaseListResp;
import com.rag.cn.yuetaoragbackend.framework.convention.Result;
import com.rag.cn.yuetaoragbackend.framework.web.Results;
import com.rag.cn.yuetaoragbackend.service.KnowledgeBaseService;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author zrq
 * 2026/04/22 15:20
 */
@RestController
@RequestMapping("/knowledge-bases")
@RequiredArgsConstructor
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    @PostMapping("/create")
    public Result<KnowledgeBaseCreateResp> createKnowledgeBase(@RequestBody CreateKnowledgeBaseReq requestParam) {
        return Results.success(knowledgeBaseService.createKnowledgeBase(requestParam));
    }

    @GetMapping("/list")
    public Result<List<KnowledgeBaseListResp>> listKnowledgeBases() {
        return Results.success(knowledgeBaseService.listKnowledgeBases());
    }

    @GetMapping("/detail/{id}")
    public Result<KnowledgeBaseDetailResp> getKnowledgeBase(@PathVariable("id") Long id) {
        return Results.success(knowledgeBaseService.getKnowledgeBase(id));
    }
}
