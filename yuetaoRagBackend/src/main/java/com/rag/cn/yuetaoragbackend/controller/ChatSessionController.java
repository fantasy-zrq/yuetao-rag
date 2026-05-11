package com.rag.cn.yuetaoragbackend.controller;

import com.rag.cn.yuetaoragbackend.dto.req.CreateChatSessionReq;
import com.rag.cn.yuetaoragbackend.dto.req.DeleteChatSessionReq;
import com.rag.cn.yuetaoragbackend.dto.resp.ChatSessionCreateResp;
import com.rag.cn.yuetaoragbackend.dto.resp.ChatSessionDetailResp;
import com.rag.cn.yuetaoragbackend.dto.resp.ChatSessionListResp;
import com.rag.cn.yuetaoragbackend.framework.convention.Result;
import com.rag.cn.yuetaoragbackend.framework.web.Results;
import com.rag.cn.yuetaoragbackend.service.ChatSessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @author zrq
 * 2026/04/22 15:20
 */
@RestController
@RequestMapping("/chat-sessions")
@RequiredArgsConstructor
public class ChatSessionController {

    private final ChatSessionService chatSessionService;

    @PostMapping("/create")
    public Result<ChatSessionCreateResp> createChatSession(@Valid @RequestBody CreateChatSessionReq requestParam) {
        return Results.success(chatSessionService.createChatSession(requestParam));
    }

    @GetMapping("/list")
    public Result<List<ChatSessionListResp>> listChatSessions() {
        return Results.success(chatSessionService.listByUserId());
    }

    @GetMapping("/detail/{id}")
    public Result<ChatSessionDetailResp> getChatSession(@PathVariable("id") Long id) {
        return Results.success(chatSessionService.getChatSession(id));
    }

    @PostMapping("/delete")
    public Result<Void> deleteChatSession(@Valid @RequestBody DeleteChatSessionReq requestParam) {
        chatSessionService.deleteChatSession(requestParam.getId());
        return Results.success();
    }
}
