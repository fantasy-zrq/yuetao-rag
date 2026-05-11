package com.rag.cn.yuetaoragbackend.controller;

import com.rag.cn.yuetaoragbackend.dto.req.BatchIdsReq;
import com.rag.cn.yuetaoragbackend.dto.req.CreateIntentNodeReq;
import com.rag.cn.yuetaoragbackend.dto.req.UpdateIntentNodeReq;
import com.rag.cn.yuetaoragbackend.dto.resp.IntentNodeTreeResp;
import com.rag.cn.yuetaoragbackend.framework.convention.Result;
import com.rag.cn.yuetaoragbackend.framework.web.Results;
import com.rag.cn.yuetaoragbackend.service.IntentNodeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @author zrq
 * 2026/05/11
 */
@RestController
@RequestMapping("/intent-tree")
@RequiredArgsConstructor
public class IntentTreeController {

    private final IntentNodeService intentNodeService;

    @GetMapping("/trees")
    public Result<List<IntentNodeTreeResp>> getTree() {
        return Results.success(intentNodeService.getTree());
    }

    @PostMapping
    public Result<Long> createIntentNode(@Valid @RequestBody CreateIntentNodeReq requestParam) {
        return Results.success(intentNodeService.createIntentNode(requestParam));
    }

    @PutMapping("/{id}")
    public Result<Void> updateIntentNode(@PathVariable("id") Long id,
                                         @Valid @RequestBody UpdateIntentNodeReq requestParam) {
        intentNodeService.updateIntentNode(id, requestParam);
        return Results.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteIntentNode(@PathVariable("id") Long id) {
        intentNodeService.deleteIntentNode(id);
        return Results.success();
    }

    @PostMapping("/batch/enable")
    public Result<Void> batchEnable(@Valid @RequestBody BatchIdsReq requestParam) {
        intentNodeService.batchEnableIntentNodes(requestParam);
        return Results.success();
    }

    @PostMapping("/batch/disable")
    public Result<Void> batchDisable(@Valid @RequestBody BatchIdsReq requestParam) {
        intentNodeService.batchDisableIntentNodes(requestParam);
        return Results.success();
    }

    @PostMapping("/batch/delete")
    public Result<Void> batchDelete(@Valid @RequestBody BatchIdsReq requestParam) {
        intentNodeService.batchDeleteIntentNodes(requestParam);
        return Results.success();
    }
}
