package com.rag.cn.yuetaoragbackend.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.rag.cn.yuetaoragbackend.dao.entity.KnowledgeDocumentDO;
import com.rag.cn.yuetaoragbackend.dto.req.CreateKnowledgeDocumentReq;
import java.util.List;

/**
 * @author zrq
 * 2026/04/22 15:10
 */
public interface KnowledgeDocumentService extends IService<KnowledgeDocumentDO> {

    KnowledgeDocumentDO createKnowledgeDocument(CreateKnowledgeDocumentReq requestParam);

    List<KnowledgeDocumentDO> listByKnowledgeBaseId(Long knowledgeBaseId);

    KnowledgeDocumentDO getKnowledgeDocument(Long id);
}
