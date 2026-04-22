package com.rag.cn.yuetaoragbackend.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.rag.cn.yuetaoragbackend.config.enums.CommonStatusEnum;
import com.rag.cn.yuetaoragbackend.config.enums.DeleteFlagEnum;
import com.rag.cn.yuetaoragbackend.config.enums.ParseStatusEnum;
import com.rag.cn.yuetaoragbackend.config.enums.VisibilityScopeEnum;
import com.rag.cn.yuetaoragbackend.dao.entity.KnowledgeDocumentDO;
import com.rag.cn.yuetaoragbackend.dao.mapper.KnowledgeDocumentMapper;
import com.rag.cn.yuetaoragbackend.dto.req.CreateKnowledgeDocumentReq;
import com.rag.cn.yuetaoragbackend.dto.resp.KnowledgeDocumentCreateResp;
import com.rag.cn.yuetaoragbackend.dto.resp.KnowledgeDocumentDetailResp;
import com.rag.cn.yuetaoragbackend.dto.resp.KnowledgeDocumentListResp;
import com.rag.cn.yuetaoragbackend.service.KnowledgeDocumentService;

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
public class KnowledgeDocumentServiceImpl extends ServiceImpl<KnowledgeDocumentMapper, KnowledgeDocumentDO>
        implements KnowledgeDocumentService {

    private final KnowledgeDocumentMapper knowledgeDocumentMapper;

    @Override
    public KnowledgeDocumentCreateResp createKnowledgeDocument(CreateKnowledgeDocumentReq requestParam) {
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
                .setParseStatus(requestParam.getParseStatus() == null || requestParam.getParseStatus().isBlank()
                        ? ParseStatusEnum.PENDING.getCode() : requestParam.getParseStatus())
                .setVisibilityScope(requestParam.getVisibilityScope() == null || requestParam.getVisibilityScope().isBlank()
                        ? VisibilityScopeEnum.INTERNAL.getCode() : requestParam.getVisibilityScope())
                .setMinRankLevel(requestParam.getMinRankLevel() == null ? 10 : requestParam.getMinRankLevel())
                .setStatus(requestParam.getStatus() == null || requestParam.getStatus().isBlank()
                        ? CommonStatusEnum.ENABLED.getCode() : requestParam.getStatus())
                .setCreatedBy(0L)
                .setUpdatedBy(0L);
        knowledgeDocumentMapper.insert(documentDO);
        KnowledgeDocumentCreateResp response = new KnowledgeDocumentCreateResp();
        BeanUtils.copyProperties(documentDO, response);
        return response;
    }

    @Override
    public List<KnowledgeDocumentListResp> listByKnowledgeBaseId(Long knowledgeBaseId) {
        return knowledgeDocumentMapper.selectList(Wrappers.<KnowledgeDocumentDO>lambdaQuery()
                        .eq(KnowledgeDocumentDO::getDeleteFlag, DeleteFlagEnum.NORMAL.getCode())
                        .eq(KnowledgeDocumentDO::getKnowledgeBaseId, knowledgeBaseId)
                        .orderByDesc(KnowledgeDocumentDO::getUpdateTime))
                .stream()
                .map(each -> {
                    KnowledgeDocumentListResp response = new KnowledgeDocumentListResp();
                    BeanUtils.copyProperties(each, response);
                    return response;
                })
                .toList();
    }

    @Override
    public KnowledgeDocumentDetailResp getKnowledgeDocument(Long id) {
        KnowledgeDocumentDO documentDO = knowledgeDocumentMapper.selectById(id);
        if (documentDO == null) {
            return null;
        }
        KnowledgeDocumentDetailResp response = new KnowledgeDocumentDetailResp();
        BeanUtils.copyProperties(documentDO, response);
        return response;
    }
}
