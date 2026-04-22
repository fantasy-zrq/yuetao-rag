package com.rag.cn.yuetaoragbackend.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.rag.cn.yuetaoragbackend.dao.entity.ChatMessageDO;
import com.rag.cn.yuetaoragbackend.dao.mapper.ChatMessageMapper;
import com.rag.cn.yuetaoragbackend.dto.req.CreateChatMessageReq;
import com.rag.cn.yuetaoragbackend.service.ChatMessageService;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * @author zrq
 * 2026/04/22 15:10
 */
@Service
public class ChatMessageServiceImpl extends ServiceImpl<ChatMessageMapper, ChatMessageDO>
    implements ChatMessageService {

    @Override
    public ChatMessageDO createChatMessage(CreateChatMessageReq requestParam) {
        ChatMessageDO messageDO = new ChatMessageDO()
            .setSessionId(requestParam.getSessionId())
            .setUserId(requestParam.getUserId())
            .setRole(requestParam.getRole())
            .setContent(requestParam.getContent())
            .setContentType(defaultIfBlank(requestParam.getContentType(), "TEXT"))
            .setSequenceNo(requestParam.getSequenceNo())
            .setTraceId(requestParam.getTraceId())
            .setModelProvider(requestParam.getModelProvider())
            .setModelName(requestParam.getModelName());
        save(messageDO);
        return messageDO;
    }

    @Override
    public List<ChatMessageDO> listBySessionId(Long sessionId) {
        return lambdaQuery()
            .eq(ChatMessageDO::getDeleteFlag, 0)
            .eq(ChatMessageDO::getSessionId, sessionId)
            .orderByAsc(ChatMessageDO::getSequenceNo)
            .list();
    }

    private String defaultIfBlank(String actual, String defaultValue) {
        return actual == null || actual.isBlank() ? defaultValue : actual;
    }
}
