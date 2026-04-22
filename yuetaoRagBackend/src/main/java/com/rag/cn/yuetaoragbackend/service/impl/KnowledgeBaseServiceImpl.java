package com.rag.cn.yuetaoragbackend.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.rag.cn.yuetaoragbackend.dao.entity.KnowledgeBaseDO;
import com.rag.cn.yuetaoragbackend.dao.mapper.KnowledgeBaseMapper;
import com.rag.cn.yuetaoragbackend.dto.req.CreateKnowledgeBaseReq;
import com.rag.cn.yuetaoragbackend.service.KnowledgeBaseService;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * @author zrq
 * 2026/04/22 15:10
 */
@Service
public class KnowledgeBaseServiceImpl extends ServiceImpl<KnowledgeBaseMapper, KnowledgeBaseDO>
    implements KnowledgeBaseService {

    @Override
    public KnowledgeBaseDO createKnowledgeBase(CreateKnowledgeBaseReq requestParam) {
        KnowledgeBaseDO knowledgeBaseDO = new KnowledgeBaseDO()
            .setName(requestParam.getName())
            .setDescription(requestParam.getDescription())
            .setStatus(defaultIfBlank(requestParam.getStatus(), "ENABLED"))
            .setEmbeddingModel(requestParam.getEmbeddingModel())
            .setCollectionName(requestParam.getCollectionName())
            .setCreatedBy(0L)
            .setUpdatedBy(0L);
        save(knowledgeBaseDO);
        return knowledgeBaseDO;
    }

    @Override
    public List<KnowledgeBaseDO> listKnowledgeBases() {
        return lambdaQuery()
            .eq(KnowledgeBaseDO::getDeleteFlag, 0)
            .orderByDesc(KnowledgeBaseDO::getUpdateTime)
            .list();
    }

    @Override
    public KnowledgeBaseDO getKnowledgeBase(Long id) {
        return getById(id);
    }

    private String defaultIfBlank(String actual, String defaultValue) {
        return actual == null || actual.isBlank() ? defaultValue : actual;
    }
}
