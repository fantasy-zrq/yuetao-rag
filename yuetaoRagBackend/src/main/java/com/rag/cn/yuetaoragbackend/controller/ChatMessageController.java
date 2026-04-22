package com.rag.cn.yuetaoragbackend.controller;

import com.rag.cn.yuetaoragbackend.dto.req.CreateChatMessageReq;
import com.rag.cn.yuetaoragbackend.dto.resp.ChatMessageCreateResp;
import com.rag.cn.yuetaoragbackend.dto.resp.ChatMessageDetailResp;
import com.rag.cn.yuetaoragbackend.dto.resp.ChatMessageListResp;
import com.rag.cn.yuetaoragbackend.framework.convention.Result;
import com.rag.cn.yuetaoragbackend.framework.web.Results;
import com.rag.cn.yuetaoragbackend.service.ChatMessageService;
import java.util.List;
import lombok.RequiredArgsConstructor;
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
@RequestMapping("/chat-messages")
@RequiredArgsConstructor
public class ChatMessageController {

    private final ChatMessageService chatMessageService;

    @PostMapping("/create")
    public Result<ChatMessageCreateResp> createChatMessage(@RequestBody CreateChatMessageReq requestParam) {
        return Results.success(chatMessageService.createChatMessage(requestParam));
    }

    @GetMapping("/list")
    public Result<List<ChatMessageListResp>> listChatMessages(@RequestParam("sessionId") Long sessionId) {
        return Results.success(chatMessageService.listBySessionId(sessionId));
    }

    @GetMapping("/detail/{id}")
    public Result<ChatMessageDetailResp> getChatMessage(@PathVariable("id") Long id) {
        return Results.success(chatMessageService.getChatMessage(id));
    }
}
