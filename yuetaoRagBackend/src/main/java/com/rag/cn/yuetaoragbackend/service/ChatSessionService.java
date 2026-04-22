package com.rag.cn.yuetaoragbackend.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.rag.cn.yuetaoragbackend.dao.entity.ChatSessionDO;
import com.rag.cn.yuetaoragbackend.dto.req.CreateChatSessionReq;
import java.util.List;

/**
 * @author zrq
 * 2026/04/22 15:10
 */
public interface ChatSessionService extends IService<ChatSessionDO> {

    ChatSessionDO createChatSession(CreateChatSessionReq requestParam);

    List<ChatSessionDO> listByUserId(Long userId);

    ChatSessionDO getChatSession(Long id);
}
