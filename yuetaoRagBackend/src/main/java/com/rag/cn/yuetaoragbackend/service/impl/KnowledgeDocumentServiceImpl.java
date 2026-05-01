package com.rag.cn.yuetaoragbackend.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.rag.cn.yuetaoragbackend.config.enums.CommonStatusEnum;
import com.rag.cn.yuetaoragbackend.config.enums.DeleteFlagEnum;
import com.rag.cn.yuetaoragbackend.config.enums.DocumentChunkLogOperationTypeEnum;
import com.rag.cn.yuetaoragbackend.config.enums.DocumentChunkLogStatusEnum;
import com.rag.cn.yuetaoragbackend.config.enums.ParseStatusEnum;
import com.rag.cn.yuetaoragbackend.config.enums.VisibilityScopeEnum;
import com.rag.cn.yuetaoragbackend.dao.entity.ChunkDO;
import com.rag.cn.yuetaoragbackend.dao.entity.KnowledgeBaseDO;
import com.rag.cn.yuetaoragbackend.dao.entity.KnowledgeDocumentDO;
import com.rag.cn.yuetaoragbackend.dao.entity.ChunkDepartmentAuthDO;
import com.rag.cn.yuetaoragbackend.dao.entity.ChunkVectorDO;
import com.rag.cn.yuetaoragbackend.dao.entity.DocumentChunkLogDO;
import com.rag.cn.yuetaoragbackend.dao.entity.DocumentDepartmentAuthDO;
import com.rag.cn.yuetaoragbackend.dao.mapper.ChunkDepartmentAuthMapper;
import com.rag.cn.yuetaoragbackend.dao.mapper.ChunkMapper;
import com.rag.cn.yuetaoragbackend.dao.mapper.ChunkVectorMapper;
import com.rag.cn.yuetaoragbackend.dao.mapper.DocumentChunkLogMapper;
import com.rag.cn.yuetaoragbackend.dao.mapper.DocumentDepartmentAuthMapper;
import com.rag.cn.yuetaoragbackend.dao.mapper.KnowledgeBaseMapper;
import com.rag.cn.yuetaoragbackend.dao.mapper.KnowledgeDocumentMapper;
import com.rag.cn.yuetaoragbackend.dto.req.CreateKnowledgeDocumentReq;
import com.rag.cn.yuetaoragbackend.dto.req.DeleteKnowledgeDocumentReq;
import com.rag.cn.yuetaoragbackend.dto.req.SplitKnowledgeDocumentReq;
import com.rag.cn.yuetaoragbackend.dto.req.UpdateKnowledgeDocumentReq;
import com.rag.cn.yuetaoragbackend.dto.resp.KnowledgeDocumentCreateResp;
import com.rag.cn.yuetaoragbackend.dto.resp.KnowledgeDocumentDetailResp;
import com.rag.cn.yuetaoragbackend.dto.resp.KnowledgeDocumentListResp;
import com.rag.cn.yuetaoragbackend.framework.context.UserContext;
import com.rag.cn.yuetaoragbackend.framework.exception.ClientException;
import com.rag.cn.yuetaoragbackend.mq.event.KnowledgeDocumentSplitEvent;
import com.rag.cn.yuetaoragbackend.mq.producer.MessageQueueProducer;
import com.rag.cn.yuetaoragbackend.framework.exception.kb.KnowledgeBaseNotFoundException;
import com.rag.cn.yuetaoragbackend.service.KnowledgeDocumentService;
import com.rag.cn.yuetaoragbackend.service.file.FileService;
import com.rag.cn.yuetaoragbackend.service.file.UploadObjectResult;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.Date;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * @author zrq
 * 2026/04/22 15:10
 */
@Service
@RequiredArgsConstructor
public class KnowledgeDocumentServiceImpl extends ServiceImpl<KnowledgeDocumentMapper, KnowledgeDocumentDO>
        implements KnowledgeDocumentService {

    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final ChunkMapper chunkMapper;
    private final ChunkVectorMapper chunkVectorMapper;
    private final DocumentDepartmentAuthMapper documentDepartmentAuthMapper;
    private final ChunkDepartmentAuthMapper chunkDepartmentAuthMapper;
    private final DocumentChunkLogMapper documentChunkLogMapper;
    private final FileService fileService;
    private final MessageQueueProducer messageQueueProducer;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeDocumentCreateResp createKnowledgeDocument(MultipartFile file, CreateKnowledgeDocumentReq requestParam) {
        validateCreateRequest(file, requestParam);
        String visibilityScope = resolveCreateVisibilityScope(requestParam.getVisibilityScope());
        List<Long> authorizedDepartmentIds = resolveAuthorizedDepartmentIds(
                visibilityScope,
                requestParam.getAuthorizedDepartmentIds());
        KnowledgeBaseDO knowledgeBaseDO = knowledgeBaseMapper.selectOne(Wrappers.<KnowledgeBaseDO>lambdaQuery()
                .eq(KnowledgeBaseDO::getId, requestParam.getKnowledgeBaseId())
                .eq(KnowledgeBaseDO::getDeleteFlag, DeleteFlagEnum.NORMAL.getCode()));
        if (knowledgeBaseDO == null) {
            throw new KnowledgeBaseNotFoundException(requestParam.getKnowledgeBaseId());
        }
        Long documentId = IdWorker.getId();
        String originalFilename = normalizedFilename(file.getOriginalFilename());
        String objectKey = documentId + "_" + System.currentTimeMillis() + "_" + originalFilename;
        UploadObjectResult uploadObjectResult = uploadFile(knowledgeBaseDO.getCollectionName(), objectKey, file);
        Long currentUserId = currentUserId();
        KnowledgeDocumentDO documentDO = new KnowledgeDocumentDO()
                .setKnowledgeBaseId(requestParam.getKnowledgeBaseId())
                .setTitle(originalFilename)
                .setSourceType("UPLOAD")
                .setMimeType(file.getContentType())
                .setStorageBucket(knowledgeBaseDO.getCollectionName())
                .setStorageKey(objectKey)
                .setStorageEtag(uploadObjectResult.getEtag())
                .setStorageUrl(uploadObjectResult.getUrl())
                .setFileSize(file.getSize())
                .setParseStatus(ParseStatusEnum.PENDING.getCode())
                .setChunkMode(requestParam.getChunkMode())
                .setChunkConfig(requestParam.getChunkConfig())
                .setVisibilityScope(visibilityScope)
                .setMinRankLevel(requestParam.getMinRankLevel() == null ? 10 : requestParam.getMinRankLevel())
                .setStatus(CommonStatusEnum.ENABLED.getCode())
                .setCreatedBy(currentUserId)
                .setUpdatedBy(currentUserId);
        documentDO.setId(documentId);
        knowledgeDocumentMapper.insert(documentDO);
        replaceDocumentDepartmentAuth(documentId, documentDO.getVisibilityScope(), authorizedDepartmentIds);
        KnowledgeDocumentCreateResp response = new KnowledgeDocumentCreateResp();
        BeanUtils.copyProperties(documentDO, response);
        response.setAuthorizedDepartmentIds(authorizedDepartmentIds);
        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeDocumentDetailResp updateKnowledgeDocument(UpdateKnowledgeDocumentReq requestParam) {
        KnowledgeDocumentDO documentDO = getNormalDocumentOrThrow(requestParam.getId());
        if (ParseStatusEnum.PROCESSING.getCode().equals(documentDO.getParseStatus())) {
            throw new ClientException("文档处理中，禁止修改分块参数");
        }
        String visibilityScope = resolveUpdateVisibilityScope(requestParam.getVisibilityScope());
        List<Long> authorizedDepartmentIds = resolveAuthorizedDepartmentIds(
                visibilityScope,
                requestParam.getAuthorizedDepartmentIds());
        boolean chunkChanged = hasChunkConfigChanged(documentDO, requestParam);
        if (chunkChanged) {
            dispatchRebuildSplit(documentDO, updateDO -> {
                updateDO.setTitle(requestParam.getTitle());
                updateDO.setVisibilityScope(visibilityScope);
                updateDO.setMinRankLevel(requestParam.getMinRankLevel());
                updateDO.setChunkMode(requestParam.getChunkMode());
                updateDO.setChunkConfig(requestParam.getChunkConfig());
                replaceDocumentDepartmentAuth(documentDO.getId(), visibilityScope, authorizedDepartmentIds);
            });
            documentDO.setChunkMode(requestParam.getChunkMode());
            documentDO.setChunkConfig(requestParam.getChunkConfig());
            documentDO.setParseStatus(ParseStatusEnum.PROCESSING.getCode());
        } else {
            KnowledgeDocumentDO updateDO = new KnowledgeDocumentDO();
            updateDO.setId(requestParam.getId());
            updateDO.setTitle(requestParam.getTitle());
            updateDO.setVisibilityScope(visibilityScope);
            updateDO.setMinRankLevel(requestParam.getMinRankLevel());
            updateDO.setUpdatedBy(currentUserId());
            knowledgeDocumentMapper.updateById(updateDO);
            replaceDocumentDepartmentAuth(documentDO.getId(), visibilityScope, authorizedDepartmentIds);
        }
        documentDO.setTitle(requestParam.getTitle());
        documentDO.setVisibilityScope(visibilityScope);
        documentDO.setMinRankLevel(requestParam.getMinRankLevel());
        documentDO.setUpdatedBy(currentUserId());
        KnowledgeDocumentDetailResp response = new KnowledgeDocumentDetailResp();
        BeanUtils.copyProperties(documentDO, response);
        response.setAuthorizedDepartmentIds(authorizedDepartmentIds);
        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteKnowledgeDocument(DeleteKnowledgeDocumentReq requestParam) {
        KnowledgeDocumentDO documentDO = getNormalDocumentOrThrow(requestParam.getId());
        if (ParseStatusEnum.PROCESSING.getCode().equals(documentDO.getParseStatus())) {
            throw new ClientException("文档处理中，禁止删除");
        }
        fileService.deleteObject(documentDO.getStorageBucket(), documentDO.getStorageKey());
        cleanupGeneratedArtifacts(requestParam.getId());
        documentDepartmentAuthMapper.delete(Wrappers.<DocumentDepartmentAuthDO>lambdaQuery()
                .eq(DocumentDepartmentAuthDO::getDocumentId, requestParam.getId()));
        KnowledgeDocumentDO deleteDO = new KnowledgeDocumentDO();
        deleteDO.setId(requestParam.getId());
        deleteDO.setDeleteFlag(DeleteFlagEnum.DELETED.getCode());
        deleteDO.setUpdatedBy(currentUserId());
        knowledgeDocumentMapper.updateById(deleteDO);
    }

    @Override
    public void splitKnowledgeDocument(SplitKnowledgeDocumentReq requestParam) {
        if (requestParam.getDocumentId() == null) {
            throw new ClientException("documentId 不能为空");
        }
        KnowledgeDocumentDO documentDO = getNormalDocumentOrThrow(requestParam.getDocumentId());
        if (ParseStatusEnum.PROCESSING.getCode().equals(documentDO.getParseStatus())) {
            throw new ClientException("文档处理中，禁止重复分块");
        }
        dispatchSplit(documentDO);
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
        response.setAuthorizedDepartmentIds(VisibilityScopeEnum.SENSITIVE.getCode().equals(documentDO.getVisibilityScope())
                ? listDocumentDepartmentIds(id)
                : List.of());
        return response;
    }

    private String resolveCreateVisibilityScope(String visibilityScope) {
        if (!StringUtils.hasText(visibilityScope)) {
            return VisibilityScopeEnum.INTERNAL.getCode();
        }
        return normalizeVisibilityScopeOrThrow(visibilityScope);
    }

    private String resolveUpdateVisibilityScope(String visibilityScope) {
        if (!StringUtils.hasText(visibilityScope)) {
            throw new ClientException("visibilityScope 不能为空");
        }
        return normalizeVisibilityScopeOrThrow(visibilityScope);
    }

    private String normalizeVisibilityScopeOrThrow(String visibilityScope) {
        String normalized = visibilityScope.trim();
        for (VisibilityScopeEnum each : VisibilityScopeEnum.values()) {
            if (each.getCode().equals(normalized)) {
                return normalized;
            }
        }
        throw new ClientException("visibilityScope 非法：" + visibilityScope);
    }

    private List<Long> resolveAuthorizedDepartmentIds(String visibilityScope, List<Long> authorizedDepartmentIds) {
        if (!VisibilityScopeEnum.SENSITIVE.getCode().equals(visibilityScope)) {
            return List.of();
        }
        List<Long> normalizedIds = authorizedDepartmentIds == null ? List.of() : authorizedDepartmentIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (normalizedIds.isEmpty()) {
            throw new ClientException("SENSITIVE 文档必须指定授权部门");
        }
        return normalizedIds;
    }

    private void replaceDocumentDepartmentAuth(Long documentId, String visibilityScope, List<Long> authorizedDepartmentIds) {
        documentDepartmentAuthMapper.delete(Wrappers.<DocumentDepartmentAuthDO>lambdaQuery()
                .eq(DocumentDepartmentAuthDO::getDocumentId, documentId));
        if (!VisibilityScopeEnum.SENSITIVE.getCode().equals(visibilityScope)) {
            return;
        }
        for (Long departmentId : authorizedDepartmentIds) {
            documentDepartmentAuthMapper.insert(new DocumentDepartmentAuthDO()
                    .setDocumentId(documentId)
                    .setDepartmentId(departmentId));
        }
    }

    private List<Long> listDocumentDepartmentIds(Long documentId) {
        return documentDepartmentAuthMapper.selectList(Wrappers.<DocumentDepartmentAuthDO>lambdaQuery()
                        .eq(DocumentDepartmentAuthDO::getDocumentId, documentId)
                        .eq(DocumentDepartmentAuthDO::getDeleteFlag, DeleteFlagEnum.NORMAL.getCode()))
                .stream()
                .map(DocumentDepartmentAuthDO::getDepartmentId)
                .toList();
    }

    private void validateCreateRequest(MultipartFile file, CreateKnowledgeDocumentReq requestParam) {
        if (requestParam.getKnowledgeBaseId() == null) {
            throw new ClientException("knowledgeBaseId 不能为空");
        }
        if (file == null || file.isEmpty()) {
            throw new ClientException("上传文件不能为空");
        }
        if (!StringUtils.hasText(requestParam.getChunkMode())) {
            throw new ClientException("chunkMode 不能为空");
        }
        if (!StringUtils.hasText(requestParam.getChunkConfig())) {
            throw new ClientException("chunkConfig 不能为空");
        }
        String filename = normalizedFilename(file.getOriginalFilename());
        if (!isSupportedFile(filename)) {
            throw new ClientException("暂不支持的文件类型：" + filename);
        }
    }

    private UploadObjectResult uploadFile(String bucketName, String objectKey, MultipartFile file) {
        try {
            return fileService.uploadObject(bucketName, objectKey, file.getBytes(), file.getContentType());
        } catch (IOException ex) {
            throw new ClientException("读取上传文件失败");
        }
    }

    private String normalizedFilename(String originalFilename) {
        String normalized = StringUtils.cleanPath(originalFilename == null ? "" : originalFilename);
        int lastSlash = Math.max(normalized.lastIndexOf('/'), normalized.lastIndexOf('\\'));
        return lastSlash >= 0 ? normalized.substring(lastSlash + 1) : normalized;
    }

    private boolean isSupportedFile(String filename) {
        String lowerCase = filename.toLowerCase();
        return lowerCase.endsWith(".pdf")
                || lowerCase.endsWith(".md")
                || lowerCase.endsWith(".doc")
                || lowerCase.endsWith(".docx")
                || lowerCase.endsWith(".txt");
    }

    private Long currentUserId() {
        try {
            return Long.parseLong(UserContext.requireUser().getUserId());
        } catch (NumberFormatException ex) {
            throw new ClientException("当前登录用户ID非法");
        }
    }

    private KnowledgeDocumentDO getNormalDocumentOrThrow(Long id) {
        KnowledgeDocumentDO documentDO = knowledgeDocumentMapper.selectOne(Wrappers.<KnowledgeDocumentDO>lambdaQuery()
                .eq(KnowledgeDocumentDO::getId, id)
                .eq(KnowledgeDocumentDO::getDeleteFlag, DeleteFlagEnum.NORMAL.getCode()));
        if (documentDO == null) {
            throw new ClientException("文档不存在或已删除：" + id);
        }
        return documentDO;
    }

    private boolean hasChunkConfigChanged(KnowledgeDocumentDO existing, UpdateKnowledgeDocumentReq requestParam) {
        return !Objects.equals(existing.getChunkMode(), requestParam.getChunkMode())
                || !Objects.equals(existing.getChunkConfig(), requestParam.getChunkConfig());
    }

    private void cleanupGeneratedArtifacts(Long documentId) {
        chunkVectorMapper.delete(new QueryWrapper<ChunkVectorDO>()
                .apply("metadata->>'document_id' = {0}", String.valueOf(documentId)));
        List<Long> chunkIds = chunkMapper.selectList(new QueryWrapper<ChunkDO>()
                        .select("id")
                        .eq("document_id", documentId))
                .stream()
                .map(ChunkDO::getId)
                .toList();
        if (!chunkIds.isEmpty()) {
            chunkDepartmentAuthMapper.delete(Wrappers.<ChunkDepartmentAuthDO>lambdaQuery()
                    .in(ChunkDepartmentAuthDO::getChunkId, chunkIds));
        }
        chunkMapper.delete(Wrappers.<ChunkDO>lambdaQuery()
                .eq(ChunkDO::getDocumentId, documentId));
    }

    /**
     * 第一次分块处理
     *
     * @param documentDO 文档
     */
    private void dispatchSplit(KnowledgeDocumentDO documentDO) {
        Long currentUserId = currentUserId();
        Long chunkLogId = IdWorker.getId();
        messageQueueProducer.sendInTransaction(
                KnowledgeDocumentSplitServiceImpl.SPLIT_TOPIC,
                String.valueOf(documentDO.getId()),
                "文档切片",
                new KnowledgeDocumentSplitEvent(documentDO.getId(), chunkLogId),
                ignored -> {
                    insertChunkLog(
                            chunkLogId,
                            documentDO,
                            DocumentChunkLogOperationTypeEnum.SPLIT.getCode(),
                            documentDO.getChunkMode(),
                            documentDO.getChunkConfig(),
                            currentUserId);
                    KnowledgeDocumentDO updateDO = new KnowledgeDocumentDO();
                    updateDO.setId(documentDO.getId());
                    updateDO.setParseStatus(ParseStatusEnum.PROCESSING.getCode());
                    updateDO.setUpdatedBy(currentUserId);
                    knowledgeDocumentMapper.updateById(updateDO);
                });
    }

    /**
     * 重新处理分块
     *
     * @param documentDO       文档
     * @param updateCustomizer 更新文档信息
     */
    private void dispatchRebuildSplit(KnowledgeDocumentDO documentDO, Consumer<KnowledgeDocumentDO> updateCustomizer) {
        Long currentUserId = currentUserId();
        Long chunkLogId = IdWorker.getId();
        messageQueueProducer.sendInTransaction(
                KnowledgeDocumentSplitServiceImpl.SPLIT_TOPIC,
                String.valueOf(documentDO.getId()),
                "文档重建切片",
                new KnowledgeDocumentSplitEvent(documentDO.getId(), chunkLogId),
                ignored -> {
                    cleanupGeneratedArtifacts(documentDO.getId());
                    KnowledgeDocumentDO updateDO = new KnowledgeDocumentDO();
                    updateDO.setId(documentDO.getId());
                    updateCustomizer.accept(updateDO);
                    insertChunkLog(
                            chunkLogId,
                            documentDO,
                            DocumentChunkLogOperationTypeEnum.REBUILD.getCode(),
                            updateDO.getChunkMode() == null ? documentDO.getChunkMode() : updateDO.getChunkMode(),
                            updateDO.getChunkConfig() == null ? documentDO.getChunkConfig() : updateDO.getChunkConfig(),
                            currentUserId);
                    updateDO.setParseStatus(ParseStatusEnum.PROCESSING.getCode());
                    updateDO.setUpdatedBy(currentUserId);
                    knowledgeDocumentMapper.updateById(updateDO);
                });
    }

    private void insertChunkLog(Long chunkLogId, KnowledgeDocumentDO documentDO, String operationType,
                                String chunkMode, String chunkConfig, Long currentUserId) {
        DocumentChunkLogDO logDO = new DocumentChunkLogDO()
                .setDocumentId(documentDO.getId())
                .setKnowledgeBaseId(documentDO.getKnowledgeBaseId())
                .setOperationType(operationType)
                .setStatus(DocumentChunkLogStatusEnum.PROCESSING.getCode())
                .setChunkMode(chunkMode)
                .setChunkConfig(chunkConfig)
                .setChunkCount(0)
                .setSplitCostMillis(0L)
                .setVectorCostMillis(0L)
                .setTotalCostMillis(0L)
                .setStartTime(new Date())
                .setCreatedBy(currentUserId)
                .setUpdatedBy(currentUserId);
        logDO.setId(chunkLogId);
        documentChunkLogMapper.insert(logDO);
    }
}
