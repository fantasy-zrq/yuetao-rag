package com.rag.cn.yuetaoragbackend.controller;

import com.rag.cn.yuetaoragbackend.dao.entity.ChatMessageDO;
import com.rag.cn.yuetaoragbackend.dto.req.CreateChatMessageReq;
import com.rag.cn.yuetaoragbackend.dto.resp.ChatMessageResp;
import com.rag.cn.yuetaoragbackend.framework.convention.Result;
import com.rag.cn.yuetaoragbackend.framework.web.Results;
import com.rag.cn.yuetaoragbackend.service.ChatMessageService;
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
@RequestMapping("/chat-messages")
public class ChatMessageController {

    private final ChatMessageService chatMessageService;

    public ChatMessageController(ChatMessageService chatMessageService) {
        this.chatMessageService = chatMessageService;
    }

    @PostMapping
    public Result<ChatMessageResp> createChatMessage(@RequestBody CreateChatMessageReq requestParam) {
        return Results.success(toChatMessageResp(chatMessageService.createChatMessage(requestParam)));
    }

    @GetMapping
    public Result<List<ChatMessageResp>> listChatMessages(@RequestParam("sessionId") Long sessionId) {
        return Results.success(chatMessageService.listBySessionId(sessionId).stream().map(this::toChatMessageResp).toList());
    }

    @GetMapping("/{id}")
    public Result<ChatMessageResp> getChatMessage(@PathVariable("id") Long id) {
        return Results.success(toChatMessageResp(chatMessageService.getById(id)));
    }

    private ChatMessageResp toChatMessageResp(ChatMessageDO chatMessageDO) {
        if (chatMessageDO == null) {
            return null;
        }
        ChatMessageResp response = new ChatMessageResp();
        BeanUtils.copyProperties(chatMessageDO, response);
        return response;
    }
}
