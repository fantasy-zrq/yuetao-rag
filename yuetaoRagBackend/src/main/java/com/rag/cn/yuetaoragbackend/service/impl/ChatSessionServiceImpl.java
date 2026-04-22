package com.rag.cn.yuetaoragbackend.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.rag.cn.yuetaoragbackend.dao.entity.ChatSessionDO;
import com.rag.cn.yuetaoragbackend.dao.mapper.ChatSessionMapper;
import com.rag.cn.yuetaoragbackend.dto.req.CreateChatSessionReq;
import com.rag.cn.yuetaoragbackend.service.ChatSessionService;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * @author zrq
 * 2026/04/22 15:10
 */
@Service
public class ChatSessionServiceImpl extends ServiceImpl<ChatSessionMapper, ChatSessionDO>
    implements ChatSessionService {

    @Override
    public ChatSessionDO createChatSession(CreateChatSessionReq requestParam) {
        ChatSessionDO sessionDO = new ChatSessionDO()
            .setUserId(requestParam.getUserId())
            .setTitle(requestParam.getTitle())
            .setStatus(defaultIfBlank(requestParam.getStatus(), "ACTIVE"));
        save(sessionDO);
        return sessionDO;
    }

    @Override
    public List<ChatSessionDO> listByUserId(Long userId) {
        return lambdaQuery()
            .eq(ChatSessionDO::getDeleteFlag, 0)
            .eq(ChatSessionDO::getUserId, userId)
            .orderByDesc(ChatSessionDO::getLastActiveAt)
            .list();
    }

    @Override
    public ChatSessionDO getChatSession(Long id) {
        return getById(id);
    }

    private String defaultIfBlank(String actual, String defaultValue) {
        return actual == null || actual.isBlank() ? defaultValue : actual;
    }
}
