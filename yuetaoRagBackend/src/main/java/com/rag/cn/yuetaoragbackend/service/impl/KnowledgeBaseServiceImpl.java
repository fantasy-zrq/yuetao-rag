package com.rag.cn.yuetaoragbackend.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.rag.cn.yuetaoragbackend.config.enums.CommonStatusEnum;
import com.rag.cn.yuetaoragbackend.config.enums.DeleteFlagEnum;
import com.rag.cn.yuetaoragbackend.dao.entity.KnowledgeBaseDO;
import com.rag.cn.yuetaoragbackend.dao.entity.KnowledgeDocumentDO;
import com.rag.cn.yuetaoragbackend.dao.mapper.KnowledgeBaseMapper;
import com.rag.cn.yuetaoragbackend.dao.mapper.KnowledgeDocumentMapper;
import com.rag.cn.yuetaoragbackend.dto.req.CreateKnowledgeBaseReq;
import com.rag.cn.yuetaoragbackend.dto.req.DeleteKnowledgeBaseReq;
import com.rag.cn.yuetaoragbackend.dto.req.UpdateKnowledgeBaseReq;
import com.rag.cn.yuetaoragbackend.dto.resp.KnowledgeBaseCreateResp;
import com.rag.cn.yuetaoragbackend.dto.resp.KnowledgeBaseDetailResp;
import com.rag.cn.yuetaoragbackend.dto.resp.KnowledgeBaseListResp;
import com.rag.cn.yuetaoragbackend.framework.context.UserContext;
import com.rag.cn.yuetaoragbackend.framework.exception.ClientException;
import com.rag.cn.yuetaoragbackend.framework.exception.kb.InvalidKnowledgeBaseCollectionNameException;
import com.rag.cn.yuetaoragbackend.framework.exception.kb.KnowledgeBaseCollectionNameAlreadyExistsException;
import com.rag.cn.yuetaoragbackend.framework.exception.kb.KnowledgeBaseHasDocumentsException;
import com.rag.cn.yuetaoragbackend.framework.exception.kb.KnowledgeBaseNameAlreadyExistsException;
import com.rag.cn.yuetaoragbackend.framework.exception.kb.KnowledgeBaseNotFoundException;
import com.rag.cn.yuetaoragbackend.service.KnowledgeBaseService;
import com.rag.cn.yuetaoragbackend.service.file.FileService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author zrq
 * 2026/04/22 15:10
 */
@Service
@RequiredArgsConstructor
public class KnowledgeBaseServiceImpl extends ServiceImpl<KnowledgeBaseMapper, KnowledgeBaseDO>
        implements KnowledgeBaseService {

    private static final String COLLECTION_NAME_PATTERN = "^[a-z0-9](?:[a-z0-9.-]{1,18}[a-z0-9])$";

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final FileService fileService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeBaseCreateResp createKnowledgeBase(CreateKnowledgeBaseReq requestParam) {
        validateCollectionName(requestParam.getCollectionName());
        if (existsByName(requestParam.getName(), null)) {
            throw new KnowledgeBaseNameAlreadyExistsException(requestParam.getName());
        }
        if (existsByCollectionName(requestParam.getCollectionName())) {
            throw new KnowledgeBaseCollectionNameAlreadyExistsException(requestParam.getCollectionName());
        }
        Long currentUserId = currentUserId();
        fileService.createBucketIfAbsent(requestParam.getCollectionName());
        KnowledgeBaseDO knowledgeBaseDO = new KnowledgeBaseDO()
                .setName(requestParam.getName())
                .setDescription(requestParam.getDescription())
                .setStatus(requestParam.getStatus() == null || requestParam.getStatus().isBlank()
                        ? CommonStatusEnum.ENABLED.getCode() : requestParam.getStatus())
                .setEmbeddingModel(requestParam.getEmbeddingModel())
                .setCollectionName(requestParam.getCollectionName())
                .setCreatedBy(currentUserId)
                .setUpdatedBy(currentUserId);
        knowledgeBaseMapper.insert(knowledgeBaseDO);
        KnowledgeBaseCreateResp response = new KnowledgeBaseCreateResp();
        BeanUtils.copyProperties(knowledgeBaseDO, response);
        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeBaseDetailResp updateKnowledgeBase(UpdateKnowledgeBaseReq requestParam) {
        KnowledgeBaseDO knowledgeBaseDO = getNormalKnowledgeBaseOrThrow(requestParam.getId());
        if (existsByName(requestParam.getName(), requestParam.getId())) {
            throw new KnowledgeBaseNameAlreadyExistsException(requestParam.getName());
        }
        Long currentUserId = currentUserId();
        KnowledgeBaseDO updateDO = new KnowledgeBaseDO();
        updateDO.setId(requestParam.getId());
        updateDO.setName(requestParam.getName());
        updateDO.setUpdatedBy(currentUserId);
        knowledgeBaseMapper.updateById(updateDO);
        knowledgeBaseDO.setName(requestParam.getName());
        knowledgeBaseDO.setUpdatedBy(currentUserId);
        KnowledgeBaseDetailResp response = new KnowledgeBaseDetailResp();
        BeanUtils.copyProperties(knowledgeBaseDO, response);
        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteKnowledgeBase(DeleteKnowledgeBaseReq requestParam) {
        KnowledgeBaseDO knowledgeBaseDO = getNormalKnowledgeBaseOrThrow(requestParam.getId());
        Long documentCount = knowledgeDocumentMapper.selectCount(Wrappers.<KnowledgeDocumentDO>lambdaQuery()
                .eq(KnowledgeDocumentDO::getKnowledgeBaseId, requestParam.getId())
                .eq(KnowledgeDocumentDO::getDeleteFlag, DeleteFlagEnum.NORMAL.getCode()));
        if (documentCount != null && documentCount > 0) {
            throw new KnowledgeBaseHasDocumentsException(requestParam.getId());
        }
        fileService.deleteBucket(knowledgeBaseDO.getCollectionName());
        KnowledgeBaseDO deleteDO = new KnowledgeBaseDO();
        deleteDO.setId(requestParam.getId());
        deleteDO.setDeleteFlag(DeleteFlagEnum.DELETED.getCode());
        deleteDO.setUpdatedBy(currentUserId());
        knowledgeBaseMapper.updateById(deleteDO);
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
        KnowledgeBaseDO knowledgeBaseDO = knowledgeBaseMapper.selectOne(Wrappers.<KnowledgeBaseDO>lambdaQuery()
                .eq(KnowledgeBaseDO::getId, id)
                .eq(KnowledgeBaseDO::getDeleteFlag, DeleteFlagEnum.NORMAL.getCode()));
        if (knowledgeBaseDO == null) {
            return null;
        }
        KnowledgeBaseDetailResp response = new KnowledgeBaseDetailResp();
        BeanUtils.copyProperties(knowledgeBaseDO, response);
        return response;
    }

    private KnowledgeBaseDO getNormalKnowledgeBaseOrThrow(Long id) {
        KnowledgeBaseDO knowledgeBaseDO = knowledgeBaseMapper.selectOne(Wrappers.<KnowledgeBaseDO>lambdaQuery()
                .eq(KnowledgeBaseDO::getId, id)
                .eq(KnowledgeBaseDO::getDeleteFlag, DeleteFlagEnum.NORMAL.getCode()));
        if (knowledgeBaseDO == null) {
            throw new KnowledgeBaseNotFoundException(id);
        }
        return knowledgeBaseDO;
    }

    private boolean existsByName(String name, Long excludeId) {
        return knowledgeBaseMapper.selectCount(Wrappers.<KnowledgeBaseDO>lambdaQuery()
                .eq(KnowledgeBaseDO::getName, name)
                .eq(KnowledgeBaseDO::getDeleteFlag, DeleteFlagEnum.NORMAL.getCode())
                .ne(excludeId != null, KnowledgeBaseDO::getId, excludeId)) > 0;
    }

    private boolean existsByCollectionName(String collectionName) {
        return knowledgeBaseMapper.selectCount(Wrappers.<KnowledgeBaseDO>lambdaQuery()
                .eq(KnowledgeBaseDO::getCollectionName, collectionName)
                .eq(KnowledgeBaseDO::getDeleteFlag, DeleteFlagEnum.NORMAL.getCode())) > 0;
    }

    private void validateCollectionName(String collectionName) {
        if (collectionName == null || !collectionName.matches(COLLECTION_NAME_PATTERN)) {
            throw new InvalidKnowledgeBaseCollectionNameException(collectionName);
        }
    }

    private Long currentUserId() {
        try {
            return Long.parseLong(UserContext.requireUser().getUserId());
        } catch (NumberFormatException ex) {
            throw new ClientException("当前登录用户ID非法");
        }
    }
}
