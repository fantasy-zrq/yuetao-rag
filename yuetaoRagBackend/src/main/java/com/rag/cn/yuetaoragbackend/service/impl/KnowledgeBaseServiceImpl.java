package com.rag.cn.yuetaoragbackend.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.rag.cn.yuetaoragbackend.config.enums.CommonStatusEnum;
import com.rag.cn.yuetaoragbackend.config.enums.DeleteFlagEnum;
import com.rag.cn.yuetaoragbackend.dao.entity.KnowledgeBaseDO;
import com.rag.cn.yuetaoragbackend.dao.mapper.KnowledgeBaseMapper;
import com.rag.cn.yuetaoragbackend.dto.req.CreateKnowledgeBaseReq;
import com.rag.cn.yuetaoragbackend.dto.resp.KnowledgeBaseCreateResp;
import com.rag.cn.yuetaoragbackend.dto.resp.KnowledgeBaseDetailResp;
import com.rag.cn.yuetaoragbackend.dto.resp.KnowledgeBaseListResp;
import com.rag.cn.yuetaoragbackend.service.KnowledgeBaseService;

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
public class KnowledgeBaseServiceImpl extends ServiceImpl<KnowledgeBaseMapper, KnowledgeBaseDO>
        implements KnowledgeBaseService {

    private final KnowledgeBaseMapper knowledgeBaseMapper;

    @Override
    public KnowledgeBaseCreateResp createKnowledgeBase(CreateKnowledgeBaseReq requestParam) {
        KnowledgeBaseDO knowledgeBaseDO = new KnowledgeBaseDO()
                .setName(requestParam.getName())
                .setDescription(requestParam.getDescription())
                .setStatus(requestParam.getStatus() == null || requestParam.getStatus().isBlank()
                        ? CommonStatusEnum.ENABLED.getCode() : requestParam.getStatus())
                .setEmbeddingModel(requestParam.getEmbeddingModel())
                .setCollectionName(requestParam.getCollectionName())
                .setCreatedBy(0L)
                .setUpdatedBy(0L);
        knowledgeBaseMapper.insert(knowledgeBaseDO);
        KnowledgeBaseCreateResp response = new KnowledgeBaseCreateResp();
        BeanUtils.copyProperties(knowledgeBaseDO, response);
        return response;
    }

    @Override
    public List<KnowledgeBaseListResp> listKnowledgeBases() {
        return knowledgeBaseMapper.selectList(Wrappers.<KnowledgeBaseDO>lambdaQuery()
                        .eq(KnowledgeBaseDO::getDeleteFlag, DeleteFlagEnum.NORMAL.getCode())
                        .orderByDesc(KnowledgeBaseDO::getUpdateTime))
                .stream()
                .map(each -> {
                    KnowledgeBaseListResp response = new KnowledgeBaseListResp();
                    BeanUtils.copyProperties(each, response);
                    return response;
                })
                .toList();
    }

    @Override
    public KnowledgeBaseDetailResp getKnowledgeBase(Long id) {
        KnowledgeBaseDO knowledgeBaseDO = knowledgeBaseMapper.selectById(id);
        if (knowledgeBaseDO == null) {
            return null;
        }
        KnowledgeBaseDetailResp response = new KnowledgeBaseDetailResp();
        BeanUtils.copyProperties(knowledgeBaseDO, response);
        return response;
    }
}
