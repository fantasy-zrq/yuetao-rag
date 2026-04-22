package com.rag.cn.yuetaoragbackend.controller;

import com.rag.cn.yuetaoragbackend.dao.entity.ChatSessionDO;
import com.rag.cn.yuetaoragbackend.dto.req.CreateChatSessionReq;
import com.rag.cn.yuetaoragbackend.dto.resp.ChatSessionResp;
import com.rag.cn.yuetaoragbackend.framework.convention.Result;
import com.rag.cn.yuetaoragbackend.framework.web.Results;
import com.rag.cn.yuetaoragbackend.service.ChatSessionService;
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
@RequestMapping("/chat-sessions")
public class ChatSessionController {

    private final ChatSessionService chatSessionService;

    public ChatSessionController(ChatSessionService chatSessionService) {
        this.chatSessionService = chatSessionService;
    }

    @PostMapping
    public Result<ChatSessionResp> createChatSession(@RequestBody CreateChatSessionReq requestParam) {
        return Results.success(toChatSessionResp(chatSessionService.createChatSession(requestParam)));
    }

    @GetMapping
    public Result<List<ChatSessionResp>> listChatSessions(@RequestParam("userId") Long userId) {
        return Results.success(chatSessionService.listByUserId(userId).stream().map(this::toChatSessionResp).toList());
    }

    @GetMapping("/{id}")
    public Result<ChatSessionResp> getChatSession(@PathVariable("id") Long id) {
        return Results.success(toChatSessionResp(chatSessionService.getChatSession(id)));
    }

    private ChatSessionResp toChatSessionResp(ChatSessionDO chatSessionDO) {
        if (chatSessionDO == null) {
            return null;
        }
        ChatSessionResp response = new ChatSessionResp();
        BeanUtils.copyProperties(chatSessionDO, response);
        return response;
    }
}
