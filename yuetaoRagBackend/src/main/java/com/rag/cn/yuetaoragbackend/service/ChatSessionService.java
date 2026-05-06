package com.rag.cn.yuetaoragbackend.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.rag.cn.yuetaoragbackend.dao.entity.ChatSessionDO;
import com.rag.cn.yuetaoragbackend.dto.req.CreateChatSessionReq;
import com.rag.cn.yuetaoragbackend.dto.resp.ChatSessionCreateResp;
import com.rag.cn.yuetaoragbackend.dto.resp.ChatSessionDetailResp;
import com.rag.cn.yuetaoragbackend.dto.resp.ChatSessionListResp;
import java.util.List;

/**
 * @author zrq
 * 2026/04/22 15:10
 */
public interface ChatSessionService extends IService<ChatSessionDO> {

    ChatSessionCreateResp createChatSession(CreateChatSessionReq requestParam);

    List<ChatSessionListResp> listByUserId(Long userId);

    ChatSessionDetailResp getChatSession(Long id);

    void deleteChatSession(Long id);
}
