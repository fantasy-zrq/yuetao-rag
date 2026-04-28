package com.rag.cn.yuetaoragbackend.job;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rag.cn.yuetaoragbackend.config.enums.DeleteFlagEnum;
import com.rag.cn.yuetaoragbackend.config.enums.ParseStatusEnum;
import com.rag.cn.yuetaoragbackend.dao.entity.KnowledgeDocumentDO;
import com.rag.cn.yuetaoragbackend.dao.mapper.KnowledgeDocumentMapper;
import com.rag.cn.yuetaoragbackend.service.impl.KnowledgeDocumentSplitServiceImpl;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * @author zrq
 * 2026/04/28 00:00
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeDocumentSplitTimeoutJobTests {

    @Mock
    private KnowledgeDocumentMapper knowledgeDocumentMapper;

    @Mock
    private KnowledgeDocumentSplitServiceImpl knowledgeDocumentSplitService;

    @InjectMocks
    private KnowledgeDocumentSplitTimeoutJob timeoutJob;

    @Test
    void shouldMarkTimedOutProcessingDocumentsAsFailed() {
        KnowledgeDocumentDO doc1 = processingDocument(100L);
        KnowledgeDocumentDO doc2 = processingDocument(200L);
        when(knowledgeDocumentMapper.selectList(any())).thenReturn(List.of(doc1, doc2));

        timeoutJob.execute();

        verify(knowledgeDocumentSplitService).markSplitFailed(100L);
        verify(knowledgeDocumentSplitService).markSplitFailed(200L);
    }

    @Test
    void shouldDoNothingWhenNoTimedOutDocuments() {
        when(knowledgeDocumentMapper.selectList(any())).thenReturn(List.of());

        timeoutJob.execute();

        verify(knowledgeDocumentSplitService, never()).markSplitFailed(any());
    }

    @Test
    void shouldContinueWhenOneDocumentFailsToUpdate() {
        KnowledgeDocumentDO doc1 = processingDocument(100L);
        KnowledgeDocumentDO doc2 = processingDocument(200L);
        when(knowledgeDocumentMapper.selectList(any())).thenReturn(List.of(doc1, doc2));
        doThrow(new RuntimeException("DB error")).when(knowledgeDocumentSplitService).markSplitFailed(100L);

        timeoutJob.execute();

        verify(knowledgeDocumentSplitService).markSplitFailed(100L);
        verify(knowledgeDocumentSplitService).markSplitFailed(200L);
    }

    private KnowledgeDocumentDO processingDocument(Long id) {
        KnowledgeDocumentDO doc = new KnowledgeDocumentDO();
        doc.setId(id);
        doc.setParseStatus(ParseStatusEnum.PROCESSING.getCode());
        doc.setDeleteFlag(DeleteFlagEnum.NORMAL.getCode());
        doc.setUpdateTime(new Date(System.currentTimeMillis() - 15 * 60 * 1000L));
        return doc;
    }
}
