package com.rag.cn.yuetaoragbackend.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.rag.cn.yuetaoragbackend.dao.entity.KnowledgeDocumentDO;
import com.rag.cn.yuetaoragbackend.dao.mapper.KnowledgeDocumentMapper;
import com.rag.cn.yuetaoragbackend.dto.req.CreateKnowledgeDocumentReq;
import com.rag.cn.yuetaoragbackend.service.KnowledgeDocumentService;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * @author zrq
 * 2026/04/22 15:10
 */
@Service
public class KnowledgeDocumentServiceImpl extends ServiceImpl<KnowledgeDocumentMapper, KnowledgeDocumentDO>
    implements KnowledgeDocumentService {

    @Override
    public KnowledgeDocumentDO createKnowledgeDocument(CreateKnowledgeDocumentReq requestParam) {
        KnowledgeDocumentDO documentDO = new KnowledgeDocumentDO()
            .setKnowledgeBaseId(requestParam.getKnowledgeBaseId())
            .setTitle(requestParam.getTitle())
            .setSourceType(requestParam.getSourceType())
            .setMimeType(requestParam.getMimeType())
            .setStorageBucket(requestParam.getStorageBucket())
            .setStorageKey(requestParam.getStorageKey())
            .setStorageEtag(requestParam.getStorageEtag())
            .setStorageUrl(requestParam.getStorageUrl())
            .setFileSize(requestParam.getFileSize())
            .setParseStatus(defaultIfBlank(requestParam.getParseStatus(), "PENDING"))
            .setVisibilityScope(defaultIfBlank(requestParam.getVisibilityScope(), "INTERNAL"))
            .setMinRankLevel(requestParam.getMinRankLevel() == null ? 10 : requestParam.getMinRankLevel())
            .setStatus(defaultIfBlank(requestParam.getStatus(), "ENABLED"))
            .setCreatedBy(0L)
            .setUpdatedBy(0L);
        save(documentDO);
        return documentDO;
    }

    @Override
    public List<KnowledgeDocumentDO> listByKnowledgeBaseId(Long knowledgeBaseId) {
        return lambdaQuery()
            .eq(KnowledgeDocumentDO::getDeleteFlag, 0)
            .eq(KnowledgeDocumentDO::getKnowledgeBaseId, knowledgeBaseId)
            .orderByDesc(KnowledgeDocumentDO::getUpdateTime)
            .list();
    }

    @Override
    public KnowledgeDocumentDO getKnowledgeDocument(Long id) {
        return getById(id);
    }

    private String defaultIfBlank(String actual, String defaultValue) {
        return actual == null || actual.isBlank() ? defaultValue : actual;
    }
}
