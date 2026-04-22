package com.rag.cn.yuetaoragbackend.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.rag.cn.yuetaoragbackend.config.enums.ChatMessageContentTypeEnum;
import com.rag.cn.yuetaoragbackend.config.enums.DeleteFlagEnum;
import com.rag.cn.yuetaoragbackend.dao.entity.ChatMessageDO;
import com.rag.cn.yuetaoragbackend.dao.mapper.ChatMessageMapper;
import com.rag.cn.yuetaoragbackend.dto.req.CreateChatMessageReq;
import com.rag.cn.yuetaoragbackend.dto.resp.ChatMessageCreateResp;
import com.rag.cn.yuetaoragbackend.dto.resp.ChatMessageDetailResp;
import com.rag.cn.yuetaoragbackend.dto.resp.ChatMessageListResp;
import com.rag.cn.yuetaoragbackend.service.ChatMessageService;

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
public class ChatMessageServiceImpl extends ServiceImpl<ChatMessageMapper, ChatMessageDO>
        implements ChatMessageService {

    private final ChatMessageMapper chatMessageMapper;

    @Override
    public ChatMessageCreateResp createChatMessage(CreateChatMessageReq requestParam) {
        ChatMessageDO messageDO = new ChatMessageDO()
                .setSessionId(requestParam.getSessionId())
                .setUserId(requestParam.getUserId())
                .setRole(requestParam.getRole())
                .setContent(requestParam.getContent())
                .setContentType(requestParam.getContentType() == null || requestParam.getContentType().isBlank()
                        ? ChatMessageContentTypeEnum.TEXT.getCode() : requestParam.getContentType())
                .setSequenceNo(requestParam.getSequenceNo())
                .setTraceId(requestParam.getTraceId())
                .setModelProvider(requestParam.getModelProvider())
                .setModelName(requestParam.getModelName());
        chatMessageMapper.insert(messageDO);
        ChatMessageCreateResp response = new ChatMessageCreateResp();
        BeanUtils.copyProperties(messageDO, response);
        return response;
    }

    @Override
    public List<ChatMessageListResp> listBySessionId(Long sessionId) {
        return chatMessageMapper.selectList(Wrappers.<ChatMessageDO>lambdaQuery()
                        .eq(ChatMessageDO::getDeleteFlag, DeleteFlagEnum.NORMAL.getCode())
                        .eq(ChatMessageDO::getSessionId, sessionId)
                        .orderByAsc(ChatMessageDO::getSequenceNo))
                .stream()
                .map(each -> {
                    ChatMessageListResp response = new ChatMessageListResp();
                    BeanUtils.copyProperties(each, response);
                    return response;
                })
                .toList();
    }

    @Override
    public ChatMessageDetailResp getChatMessage(Long id) {
        ChatMessageDO messageDO = chatMessageMapper.selectById(id);
        if (messageDO == null) {
            return null;
        }
        ChatMessageDetailResp response = new ChatMessageDetailResp();
        BeanUtils.copyProperties(messageDO, response);
        return response;
    }
}
