package com.rag.cn.yuetaoragbackend.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.rag.cn.yuetaoragbackend.config.enums.ChatSessionStatusEnum;
import com.rag.cn.yuetaoragbackend.config.enums.DeleteFlagEnum;
import com.rag.cn.yuetaoragbackend.dao.entity.ChatSessionDO;
import com.rag.cn.yuetaoragbackend.dao.mapper.ChatSessionMapper;
import com.rag.cn.yuetaoragbackend.dto.req.CreateChatSessionReq;
import com.rag.cn.yuetaoragbackend.dto.resp.ChatSessionCreateResp;
import com.rag.cn.yuetaoragbackend.dto.resp.ChatSessionDetailResp;
import com.rag.cn.yuetaoragbackend.dto.resp.ChatSessionListResp;
import com.rag.cn.yuetaoragbackend.framework.context.UserContext;
import com.rag.cn.yuetaoragbackend.framework.exception.ClientException;
import com.rag.cn.yuetaoragbackend.service.ChatSessionService;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

/**
 * @author zrq
 * 2026/04/22 15:10
 */
@Service
@RequiredArgsConstructor
public class ChatSessionServiceImpl extends ServiceImpl<ChatSessionMapper, ChatSessionDO>
        implements ChatSessionService {

    private final ChatSessionMapper chatSessionMapper;

    @Override
    public ChatSessionCreateResp createChatSession(CreateChatSessionReq requestParam) {
        Long userId = currentUserId();
        ChatSessionDO sessionDO = new ChatSessionDO()
                .setUserId(userId)
                .setTitle(requestParam.getTitle())
                .setStatus(requestParam.getStatus() == null || requestParam.getStatus().isBlank()
                        ? ChatSessionStatusEnum.ACTIVE.getCode() : requestParam.getStatus());
        chatSessionMapper.insert(sessionDO);
        ChatSessionCreateResp response = new ChatSessionCreateResp();
        BeanUtils.copyProperties(sessionDO, response);
        return response;
    }

    @Override
    public List<ChatSessionListResp> listByUserId(Long userId) {
        return chatSessionMapper.selectList(Wrappers.<ChatSessionDO>lambdaQuery()
                        .eq(ChatSessionDO::getDeleteFlag, DeleteFlagEnum.NORMAL.getCode())
                        .eq(ChatSessionDO::getUserId, userId)
                        .orderByDesc(ChatSessionDO::getLastActiveAt))
                .stream()
                .map(each -> {
                    ChatSessionListResp response = new ChatSessionListResp();
                    BeanUtils.copyProperties(each, response);
                    return response;
                })
                .toList();
    }

    @Override
    public ChatSessionDetailResp getChatSession(Long id) {
        ChatSessionDO sessionDO = chatSessionMapper.selectOne(Wrappers.<ChatSessionDO>lambdaQuery()
                .eq(ChatSessionDO::getId, id)
                .eq(ChatSessionDO::getDeleteFlag, DeleteFlagEnum.NORMAL.getCode()));
        if (sessionDO == null) {
            return null;
        }
        Long userId = currentUserId();
        if (!userId.equals(sessionDO.getUserId())) {
            throw new ClientException("无权访问该会话");
        }
        ChatSessionDetailResp response = new ChatSessionDetailResp();
        BeanUtils.copyProperties(sessionDO, response);
        return response;
    }

    @Override
    public void deleteChatSession(Long id) {
        ChatSessionDO updateSession = new ChatSessionDO();
        updateSession.setId(id);
        updateSession.setDeleteFlag(DeleteFlagEnum.DELETED.getCode());
        chatSessionMapper.updateById(updateSession);
    }

    private Long currentUserId() {
        try {
            return Long.parseLong(UserContext.requireUser().getUserId());
        } catch (NumberFormatException ex) {
            throw new ClientException("当前登录用户ID非法");
        }
    }
}
