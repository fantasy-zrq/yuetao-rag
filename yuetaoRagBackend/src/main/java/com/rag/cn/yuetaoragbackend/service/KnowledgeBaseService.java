package com.rag.cn.yuetaoragbackend.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.rag.cn.yuetaoragbackend.dao.entity.KnowledgeBaseDO;
import com.rag.cn.yuetaoragbackend.dto.req.CreateKnowledgeBaseReq;
import java.util.List;

/**
 * @author zrq
 * 2026/04/22 15:10
 */
public interface KnowledgeBaseService extends IService<KnowledgeBaseDO> {

    KnowledgeBaseDO createKnowledgeBase(CreateKnowledgeBaseReq requestParam);

    List<KnowledgeBaseDO> listKnowledgeBases();

    KnowledgeBaseDO getKnowledgeBase(Long id);
}
