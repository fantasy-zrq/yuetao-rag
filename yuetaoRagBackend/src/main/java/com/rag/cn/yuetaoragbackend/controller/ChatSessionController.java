package com.rag.cn.yuetaoragbackend.controller;

import com.rag.cn.yuetaoragbackend.dto.req.CreateChatSessionReq;
import com.rag.cn.yuetaoragbackend.dto.resp.ChatSessionCreateResp;
import com.rag.cn.yuetaoragbackend.dto.resp.ChatSessionDetailResp;
import com.rag.cn.yuetaoragbackend.dto.resp.ChatSessionListResp;
import com.rag.cn.yuetaoragbackend.framework.convention.Result;
import com.rag.cn.yuetaoragbackend.framework.web.Results;
import com.rag.cn.yuetaoragbackend.service.ChatSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

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
    public Result<ChatSessionCreateResp> createChatSession(@RequestBody CreateChatSessionReq requestParam) {
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
    public Result<Void> deleteChatSession(@RequestBody Map<String, Long> requestParam) {
        chatSessionService.deleteChatSession(requestParam.get("id"));
        return Results.success();
    }
}
