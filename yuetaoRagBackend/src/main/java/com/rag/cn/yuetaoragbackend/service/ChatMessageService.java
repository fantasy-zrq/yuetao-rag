package com.rag.cn.yuetaoragbackend.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.rag.cn.yuetaoragbackend.dao.entity.ChatMessageDO;
import com.rag.cn.yuetaoragbackend.dto.req.ChatReq;
import com.rag.cn.yuetaoragbackend.dto.req.CreateChatMessageReq;
import com.rag.cn.yuetaoragbackend.dto.resp.ChatResp;
import com.rag.cn.yuetaoragbackend.dto.resp.ChatMessageCreateResp;
import com.rag.cn.yuetaoragbackend.dto.resp.ChatMessageDetailResp;
import com.rag.cn.yuetaoragbackend.dto.resp.ChatMessageListResp;
import java.util.List;

/**
 * @author zrq
 * 2026/04/22 15:10
 */
public interface ChatMessageService extends IService<ChatMessageDO> {

    ChatResp chat(ChatReq requestParam);

    ChatMessageCreateResp createChatMessage(CreateChatMessageReq requestParam);

    List<ChatMessageListResp> listBySessionId(Long sessionId);

    ChatMessageDetailResp getChatMessage(Long id);
}
