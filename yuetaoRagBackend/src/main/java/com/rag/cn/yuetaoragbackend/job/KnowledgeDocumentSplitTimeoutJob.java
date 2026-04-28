package com.rag.cn.yuetaoragbackend.job;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.rag.cn.yuetaoragbackend.config.enums.DeleteFlagEnum;
import com.rag.cn.yuetaoragbackend.config.enums.ParseStatusEnum;
import com.rag.cn.yuetaoragbackend.dao.entity.KnowledgeDocumentDO;
import com.rag.cn.yuetaoragbackend.dao.mapper.KnowledgeDocumentMapper;
import com.rag.cn.yuetaoragbackend.service.impl.KnowledgeDocumentSplitServiceImpl;
import java.util.Date;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 文档切片超时兜底任务。
 * 扫描 PROCESSING 状态超过 10 分钟的文档，自动标记为 FAILED。
 *
 * @author zrq
 * 2026/04/28 00:00
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeDocumentSplitTimeoutJob {

    private static final long TIMEOUT_MILLIS = 10 * 60 * 1000L;

    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final KnowledgeDocumentSplitServiceImpl knowledgeDocumentSplitService;

    @Scheduled(fixedDelay = 60000)
    public void execute() {
        Date threshold = new Date(System.currentTimeMillis() - TIMEOUT_MILLIS);
        List<KnowledgeDocumentDO> timedOutDocs = knowledgeDocumentMapper.selectList(
                Wrappers.<KnowledgeDocumentDO>lambdaQuery()
                        .eq(KnowledgeDocumentDO::getParseStatus, ParseStatusEnum.PROCESSING.getCode())
                        .eq(KnowledgeDocumentDO::getDeleteFlag, DeleteFlagEnum.NORMAL.getCode())
                        .le(KnowledgeDocumentDO::getUpdateTime, threshold));
        if (timedOutDocs.isEmpty()) {
            return;
        }
        log.warn("发现 {} 个切片超时文档，开始兜底标记为 FAILED", timedOutDocs.size());
        for (KnowledgeDocumentDO doc : timedOutDocs) {
            try {
                knowledgeDocumentSplitService.markSplitFailed(doc.getId());
                log.info("文档切片超时兜底完成：documentId={}", doc.getId());
            } catch (Exception ex) {
                log.error("文档切片超时兜底失败：documentId={}", doc.getId(), ex);
            }
        }
    }
}
