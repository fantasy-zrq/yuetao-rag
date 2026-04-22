package com.rag.cn.yuetaoragbackend.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.rag.cn.yuetaoragbackend.dao.entity.KnowledgeDocumentDO;
import com.rag.cn.yuetaoragbackend.dto.req.CreateKnowledgeDocumentReq;
import com.rag.cn.yuetaoragbackend.dto.resp.KnowledgeDocumentCreateResp;
import com.rag.cn.yuetaoragbackend.dto.resp.KnowledgeDocumentDetailResp;
import com.rag.cn.yuetaoragbackend.dto.resp.KnowledgeDocumentListResp;
import java.util.List;

/**
 * @author zrq
 * 2026/04/22 15:10
 */
public interface KnowledgeDocumentService extends IService<KnowledgeDocumentDO> {

    KnowledgeDocumentCreateResp createKnowledgeDocument(CreateKnowledgeDocumentReq requestParam);

    List<KnowledgeDocumentListResp> listByKnowledgeBaseId(Long knowledgeBaseId);

    KnowledgeDocumentDetailResp getKnowledgeDocument(Long id);
}
