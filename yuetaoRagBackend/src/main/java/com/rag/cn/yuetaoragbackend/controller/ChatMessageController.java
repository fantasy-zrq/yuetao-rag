package com.rag.cn.yuetaoragbackend.controller;

import com.rag.cn.yuetaoragbackend.dto.req.ChatReq;
import com.rag.cn.yuetaoragbackend.dto.req.ChatStreamReq;
import com.rag.cn.yuetaoragbackend.dto.req.CreateChatMessageReq;
import com.rag.cn.yuetaoragbackend.dto.req.StopChatStreamReq;
import com.rag.cn.yuetaoragbackend.dto.resp.ChatMessageCreateResp;
import com.rag.cn.yuetaoragbackend.dto.resp.ChatMessageDetailResp;
import com.rag.cn.yuetaoragbackend.dto.resp.ChatMessageListResp;
import com.rag.cn.yuetaoragbackend.dto.resp.ChatResp;
import com.rag.cn.yuetaoragbackend.framework.convention.Result;
import com.rag.cn.yuetaoragbackend.framework.web.Results;
import com.rag.cn.yuetaoragbackend.service.ChatMessageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * @author zrq
 * 2026/04/22 15:20
 */
@RestController
@RequestMapping("/chat-messages")
@RequiredArgsConstructor
public class ChatMessageController {

    private final ChatMessageService chatMessageService;

    @PostMapping("/chat")
    public Result<ChatResp> chat(@Valid @RequestBody ChatReq requestParam) {
        return Results.success(chatMessageService.chat(requestParam));
    }

    @PostMapping(value = "/chatstream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@Valid @RequestBody ChatStreamReq requestParam) {
        return chatMessageService.chatStream(requestParam);
    }

    @PostMapping("/chatstream/stop")
    public Result<Boolean> stopChatStream(@Valid @RequestBody StopChatStreamReq requestParam) {
        return Results.success(chatMessageService.stopChatStream(requestParam));
    }

    @PostMapping("/create")
    public Result<ChatMessageCreateResp> createChatMessage(@Valid @RequestBody CreateChatMessageReq requestParam) {
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
