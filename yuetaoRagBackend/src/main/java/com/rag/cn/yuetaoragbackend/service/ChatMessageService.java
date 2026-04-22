package com.rag.cn.yuetaoragbackend.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.rag.cn.yuetaoragbackend.dao.entity.ChatMessageDO;
import com.rag.cn.yuetaoragbackend.dto.req.CreateChatMessageReq;
import java.util.List;

/**
 * @author zrq
 * 2026/04/22 15:10
 */
public interface ChatMessageService extends IService<ChatMessageDO> {

    ChatMessageDO createChatMessage(CreateChatMessageReq requestParam);

    List<ChatMessageDO> listBySessionId(Long sessionId);
}
