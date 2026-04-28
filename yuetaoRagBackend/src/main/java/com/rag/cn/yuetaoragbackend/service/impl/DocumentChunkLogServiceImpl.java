package com.rag.cn.yuetaoragbackend.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.rag.cn.yuetaoragbackend.config.enums.DeleteFlagEnum;
import com.rag.cn.yuetaoragbackend.config.enums.DocumentChunkLogStatusEnum;
import com.rag.cn.yuetaoragbackend.dao.entity.DocumentChunkLogDO;
import com.rag.cn.yuetaoragbackend.dao.mapper.DocumentChunkLogMapper;
import com.rag.cn.yuetaoragbackend.service.DocumentChunkLogService;
import java.util.Date;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * @author zrq
 * 2026/04/28 22:00
 */
@Service
@RequiredArgsConstructor
public class DocumentChunkLogServiceImpl implements DocumentChunkLogService {

    private final DocumentChunkLogMapper documentChunkLogMapper;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void recordSplitResult(Long chunkLogId, int chunkCount, long splitCostMillis) {
        if (chunkLogId == null) {
            return;
        }
        DocumentChunkLogDO updateDO = new DocumentChunkLogDO()
                .setChunkCount(chunkCount)
                .setSplitCostMillis(splitCostMillis);
        updateDO.setId(chunkLogId);
        documentChunkLogMapper.updateById(updateDO);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void recordVectorResult(Long chunkLogId, long vectorCostMillis) {
        if (chunkLogId == null) {
            return;
        }
        DocumentChunkLogDO updateDO = new DocumentChunkLogDO()
                .setVectorCostMillis(vectorCostMillis);
        updateDO.setId(chunkLogId);
        documentChunkLogMapper.updateById(updateDO);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void markSuccess(Long chunkLogId, int chunkCount, long splitCostMillis,
                            long vectorCostMillis, long totalCostMillis) {
        if (chunkLogId == null) {
            return;
        }
        DocumentChunkLogDO updateDO = new DocumentChunkLogDO()
                .setStatus(DocumentChunkLogStatusEnum.SUCCESS.getCode())
                .setChunkCount(chunkCount)
                .setSplitCostMillis(splitCostMillis)
                .setVectorCostMillis(vectorCostMillis)
                .setTotalCostMillis(totalCostMillis)
                .setEndTime(new Date());
        updateDO.setId(chunkLogId);
        documentChunkLogMapper.updateById(updateDO);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void markFailed(Long chunkLogId, String errorMessage) {
        markTerminal(chunkLogId, DocumentChunkLogStatusEnum.FAILED.getCode(), errorMessage);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void markTimeout(Long documentId) {
        List<DocumentChunkLogDO> logs = documentChunkLogMapper.selectList(Wrappers.<DocumentChunkLogDO>lambdaQuery()
                .eq(DocumentChunkLogDO::getDocumentId, documentId)
                .eq(DocumentChunkLogDO::getStatus, DocumentChunkLogStatusEnum.PROCESSING.getCode())
                .eq(DocumentChunkLogDO::getDeleteFlag, DeleteFlagEnum.NORMAL.getCode())
                .orderByDesc(DocumentChunkLogDO::getStartTime)
                .last("limit 1"));
        if (logs.isEmpty()) {
            return;
        }
        markTerminal(logs.get(0).getId(), DocumentChunkLogStatusEnum.TIMEOUT.getCode(), "切片超时");
    }

    private void markTerminal(Long chunkLogId, String status, String errorMessage) {
        if (chunkLogId == null) {
            return;
        }
        Date endTime = new Date();
        DocumentChunkLogDO updateDO = new DocumentChunkLogDO()
                .setStatus(status)
                .setErrorMessage(truncateErrorMessage(errorMessage))
                .setTotalCostMillis(calculateTotalCostMillis(chunkLogId, endTime))
                .setEndTime(endTime);
        updateDO.setId(chunkLogId);
        documentChunkLogMapper.updateById(updateDO);
    }

    private Long calculateTotalCostMillis(Long chunkLogId, Date endTime) {
        DocumentChunkLogDO existingLog = documentChunkLogMapper.selectById(chunkLogId);
        if (existingLog == null || existingLog.getStartTime() == null) {
            return null;
        }
        return Math.max(0L, endTime.getTime() - existingLog.getStartTime().getTime());
    }

    private String truncateErrorMessage(String errorMessage) {
        if (!StringUtils.hasText(errorMessage)) {
            return null;
        }
        return errorMessage.length() <= 4000 ? errorMessage : errorMessage.substring(0, 4000);
    }
}
