package com.rag.cn.yuetaoragbackend.controller;

import com.rag.cn.yuetaoragbackend.dao.entity.KnowledgeBaseDO;
import com.rag.cn.yuetaoragbackend.dto.req.CreateKnowledgeBaseReq;
import com.rag.cn.yuetaoragbackend.dto.resp.KnowledgeBaseResp;
import com.rag.cn.yuetaoragbackend.framework.convention.Result;
import com.rag.cn.yuetaoragbackend.framework.web.Results;
import com.rag.cn.yuetaoragbackend.service.KnowledgeBaseService;
import java.util.List;
import org.springframework.beans.BeanUtils;
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
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    public KnowledgeBaseController(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

    @PostMapping
    public Result<KnowledgeBaseResp> createKnowledgeBase(@RequestBody CreateKnowledgeBaseReq requestParam) {
        return Results.success(toKnowledgeBaseResp(knowledgeBaseService.createKnowledgeBase(requestParam)));
    }

    @GetMapping
    public Result<List<KnowledgeBaseResp>> listKnowledgeBases() {
        return Results.success(knowledgeBaseService.listKnowledgeBases().stream().map(this::toKnowledgeBaseResp).toList());
    }

    @GetMapping("/{id}")
    public Result<KnowledgeBaseResp> getKnowledgeBase(@PathVariable("id") Long id) {
        return Results.success(toKnowledgeBaseResp(knowledgeBaseService.getKnowledgeBase(id)));
    }

    private KnowledgeBaseResp toKnowledgeBaseResp(KnowledgeBaseDO knowledgeBaseDO) {
        if (knowledgeBaseDO == null) {
            return null;
        }
        KnowledgeBaseResp response = new KnowledgeBaseResp();
        BeanUtils.copyProperties(knowledgeBaseDO, response);
        return response;
    }
}
