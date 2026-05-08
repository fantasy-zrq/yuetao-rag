package com.rag.cn.yuetaoragbackend.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.rag.cn.yuetaoragbackend.config.enums.DeleteFlagEnum;
import com.rag.cn.yuetaoragbackend.config.enums.DocumentChunkLogOperationTypeEnum;
import com.rag.cn.yuetaoragbackend.config.enums.DocumentChunkLogStatusEnum;
import com.rag.cn.yuetaoragbackend.config.enums.ParseStatusEnum;
import com.rag.cn.yuetaoragbackend.dao.entity.DocumentDepartmentAuthDO;
import com.rag.cn.yuetaoragbackend.dao.entity.DocumentChunkLogDO;
import com.rag.cn.yuetaoragbackend.dao.entity.KnowledgeDocumentDO;
import com.rag.cn.yuetaoragbackend.dao.mapper.ChunkDepartmentAuthMapper;
import com.rag.cn.yuetaoragbackend.dao.mapper.ChunkMapper;
import com.rag.cn.yuetaoragbackend.dao.mapper.ChunkVectorMapper;
import com.rag.cn.yuetaoragbackend.dao.mapper.DocumentChunkLogMapper;
import com.rag.cn.yuetaoragbackend.dao.mapper.DocumentDepartmentAuthMapper;
import com.rag.cn.yuetaoragbackend.dao.mapper.KnowledgeBaseMapper;
import com.rag.cn.yuetaoragbackend.dao.mapper.KnowledgeDocumentMapper;
import com.rag.cn.yuetaoragbackend.dto.req.DeleteKnowledgeDocumentReq;
import com.rag.cn.yuetaoragbackend.dto.req.SplitKnowledgeDocumentReq;
import com.rag.cn.yuetaoragbackend.dto.req.UpdateKnowledgeDocumentReq;
import com.rag.cn.yuetaoragbackend.dto.resp.KnowledgeDocumentDetailResp;
import com.rag.cn.yuetaoragbackend.framework.context.LoginUser;
import com.rag.cn.yuetaoragbackend.framework.context.UserContext;
import com.rag.cn.yuetaoragbackend.framework.exception.ClientException;
import com.rag.cn.yuetaoragbackend.mq.event.KnowledgeDocumentSplitEvent;
import com.rag.cn.yuetaoragbackend.mq.producer.MessageQueueProducer;
import com.rag.cn.yuetaoragbackend.service.file.FileService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * @author zrq
 * 2026/04/26 17:00
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeDocumentMutationServiceImplTests {

    @Mock
    private KnowledgeDocumentMapper knowledgeDocumentMapper;

    @Mock
    private KnowledgeBaseMapper knowledgeBaseMapper;

    @Mock
    private ChunkMapper chunkMapper;

    @Mock
    private ChunkVectorMapper chunkVectorMapper;

    @Mock
    private DocumentDepartmentAuthMapper documentDepartmentAuthMapper;

    @Mock
    private ChunkDepartmentAuthMapper chunkDepartmentAuthMapper;

    @Mock
    private DocumentChunkLogMapper documentChunkLogMapper;

    @Mock
    private FileService fileService;

    @Mock
    private MessageQueueProducer messageQueueProducer;

    @InjectMocks
    private KnowledgeDocumentServiceImpl knowledgeDocumentService;

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void shouldUpdateTitleWithoutReprocessing() {
        UserContext.set(LoginUser.builder().userId("10001").build());
        KnowledgeDocumentDO existing = existingDocument(ParseStatusEnum.SUCCESS.getCode());
        UpdateKnowledgeDocumentReq request = new UpdateKnowledgeDocumentReq()
                .setId(200L)
                .setTitle("renamed.pdf")
                .setChunkMode("FIXED")
                .setChunkConfig("{\"chunkSize\":512,\"chunkOverlap\":50}")
                .setVisibilityScope("INTERNAL")
                .setMinRankLevel(10);
        when(knowledgeDocumentMapper.selectOne(any(Wrapper.class))).thenReturn(existing);
        when(knowledgeDocumentMapper.updateById(any(KnowledgeDocumentDO.class))).thenReturn(1);

        KnowledgeDocumentDetailResp response = knowledgeDocumentService.updateKnowledgeDocument(request);

        assertThat(response.getTitle()).isEqualTo("renamed.pdf");
        assertThat(response.getParseStatus()).isEqualTo(ParseStatusEnum.SUCCESS.getCode());
        verify(chunkMapper, never()).delete(any(Wrapper.class));
        verify(chunkVectorMapper, never()).delete(any(Wrapper.class));
        verify(messageQueueProducer, never()).sendInTransaction(any(), any(), any(), any(), any());
        ArgumentCaptor<KnowledgeDocumentDO> captor = ArgumentCaptor.forClass(KnowledgeDocumentDO.class);
        verify(knowledgeDocumentMapper).updateById(captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo("renamed.pdf");
        assertThat(captor.getValue().getParseStatus()).isNull();
    }

    @Test
    void shouldResetToProcessingAndCleanupArtifactsWhenChunkConfigChanges() {
        UserContext.set(LoginUser.builder().userId("10001").build());
        KnowledgeDocumentDO existing = existingDocument(ParseStatusEnum.SUCCESS.getCode());
        UpdateKnowledgeDocumentReq request = new UpdateKnowledgeDocumentReq()
                .setId(200L)
                .setTitle("manual.pdf")
                .setChunkMode("STRUCTURE_AWARE")
                .setChunkConfig("{\"chunkSize\":800,\"chunkOverlap\":80}")
                .setVisibilityScope("INTERNAL")
                .setMinRankLevel(10);
        when(knowledgeDocumentMapper.selectOne(any(Wrapper.class))).thenReturn(existing);
        when(chunkMapper.selectList(any(Wrapper.class))).thenReturn(java.util.List.of());
        doAnswer(invocation -> {
            ((java.util.function.Consumer<Object>) invocation.getArgument(4)).accept(null);
            return null;
        }).when(messageQueueProducer).sendInTransaction(any(), any(), any(), any(), any());

        KnowledgeDocumentDetailResp response = knowledgeDocumentService.updateKnowledgeDocument(request);

        assertThat(response.getParseStatus()).isEqualTo(ParseStatusEnum.PROCESSING.getCode());
        verify(chunkVectorMapper).delete(any(Wrapper.class));
        verify(chunkMapper).delete(any(Wrapper.class));
        verify(messageQueueProducer).sendInTransaction(any(), any(), any(), any(), any());

        ArgumentCaptor<DocumentChunkLogDO> logCaptor = ArgumentCaptor.forClass(DocumentChunkLogDO.class);
        verify(documentChunkLogMapper).insert(logCaptor.capture());
        DocumentChunkLogDO insertedLog = logCaptor.getValue();
        assertThat(insertedLog.getDocumentId()).isEqualTo(200L);
        assertThat(insertedLog.getKnowledgeBaseId()).isEqualTo(100L);
        assertThat(insertedLog.getOperationType()).isEqualTo(DocumentChunkLogOperationTypeEnum.REBUILD.getCode());
        assertThat(insertedLog.getStatus()).isEqualTo(DocumentChunkLogStatusEnum.PROCESSING.getCode());
        assertThat(insertedLog.getChunkMode()).isEqualTo("STRUCTURE_AWARE");
        assertThat(insertedLog.getChunkConfig()).isEqualTo("{\"chunkSize\":800,\"chunkOverlap\":80}");
        assertThat(insertedLog.getStartTime()).isNotNull();
        assertThat(insertedLog.getCreatedBy()).isEqualTo(10001L);

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(messageQueueProducer).sendInTransaction(any(), any(), any(), eventCaptor.capture(), any());
        KnowledgeDocumentSplitEvent event = (KnowledgeDocumentSplitEvent) eventCaptor.getValue();
        assertThat(event.getDocumentId()).isEqualTo(200L);
        assertThat(event.getChunkLogId()).isEqualTo(insertedLog.getId());
    }

    @Test
    void shouldClearFailReasonWhenRetryingFailedSplit() {
        UserContext.set(LoginUser.builder().userId("10001").build());
        KnowledgeDocumentDO existing = existingDocument(ParseStatusEnum.FAILED.getCode());
        existing.setFailReason("vector service unavailable");
        when(knowledgeDocumentMapper.selectOne(any(Wrapper.class))).thenReturn(existing);
        doAnswer(invocation -> {
            ((java.util.function.Consumer<Object>) invocation.getArgument(4)).accept(null);
            return null;
        }).when(messageQueueProducer).sendInTransaction(any(), any(), any(), any(), any());

        knowledgeDocumentService.splitKnowledgeDocument(new SplitKnowledgeDocumentReq().setDocumentId(200L));

        ArgumentCaptor<KnowledgeDocumentDO> documentCaptor = ArgumentCaptor.forClass(KnowledgeDocumentDO.class);
        verify(knowledgeDocumentMapper).updateById(documentCaptor.capture());
        assertThat(documentCaptor.getValue().getParseStatus()).isEqualTo(ParseStatusEnum.PROCESSING.getCode());
        assertThat(documentCaptor.getValue().getFailReason()).isNull();
        verify(messageQueueProducer).sendInTransaction(any(), any(), any(), any(), any());
    }

    @Test
    void shouldClearFailReasonWhenRetryingTimedOutSplit() {
        UserContext.set(LoginUser.builder().userId("10001").build());
        KnowledgeDocumentDO existing = existingDocument(ParseStatusEnum.FAILED.getCode());
        existing.setFailReason("切片超时");
        when(knowledgeDocumentMapper.selectOne(any(Wrapper.class))).thenReturn(existing);
        doAnswer(invocation -> {
            ((java.util.function.Consumer<Object>) invocation.getArgument(4)).accept(null);
            return null;
        }).when(messageQueueProducer).sendInTransaction(any(), any(), any(), any(), any());

        knowledgeDocumentService.splitKnowledgeDocument(new SplitKnowledgeDocumentReq().setDocumentId(200L));

        ArgumentCaptor<KnowledgeDocumentDO> documentCaptor = ArgumentCaptor.forClass(KnowledgeDocumentDO.class);
        verify(knowledgeDocumentMapper).updateById(documentCaptor.capture());
        assertThat(documentCaptor.getValue().getParseStatus()).isEqualTo(ParseStatusEnum.PROCESSING.getCode());
        assertThat(documentCaptor.getValue().getFailReason()).isNull();
        verify(messageQueueProducer).sendInTransaction(any(), any(), any(), any(), any());
    }

    @Test
    void shouldRejectDeleteWhenDocumentIsProcessing() {
        UserContext.set(LoginUser.builder().userId("10001").build());
        when(knowledgeDocumentMapper.selectOne(any(Wrapper.class))).thenReturn(existingDocument(ParseStatusEnum.PROCESSING.getCode()));

        assertThatThrownBy(() -> knowledgeDocumentService.deleteKnowledgeDocument(new DeleteKnowledgeDocumentReq().setId(200L)))
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("处理中");

        verify(fileService, never()).deleteObject(any(String.class), any(String.class));
    }

    @Test
    void shouldDeleteDocumentAndArtifactsWhenNotProcessing() {
        UserContext.set(LoginUser.builder().userId("10001").build());
        when(knowledgeDocumentMapper.selectOne(any(Wrapper.class))).thenReturn(existingDocument(ParseStatusEnum.SUCCESS.getCode()));
        when(knowledgeDocumentMapper.updateById(any(KnowledgeDocumentDO.class))).thenReturn(1);
        when(chunkMapper.selectList(any(Wrapper.class))).thenReturn(java.util.List.of());

        knowledgeDocumentService.deleteKnowledgeDocument(new DeleteKnowledgeDocumentReq().setId(200L));

        verify(fileService).deleteObject("kb-doc-01", "doc-key.pdf");
        verify(chunkVectorMapper).delete(any(Wrapper.class));
        verify(chunkMapper).delete(any(Wrapper.class));
        verify(documentDepartmentAuthMapper).delete(any(Wrapper.class));
        ArgumentCaptor<KnowledgeDocumentDO> captor = ArgumentCaptor.forClass(KnowledgeDocumentDO.class);
        verify(knowledgeDocumentMapper).updateById(captor.capture());
        assertThat(captor.getValue().getDeleteFlag()).isEqualTo(DeleteFlagEnum.DELETED.getCode());
    }

    @Test
    void shouldReplaceSensitiveDepartmentAuthWhenUpdatingDocument() {
        UserContext.set(LoginUser.builder().userId("10001").build());
        KnowledgeDocumentDO existing = existingDocument(ParseStatusEnum.SUCCESS.getCode());
        existing.setVisibilityScope("SENSITIVE");
        UpdateKnowledgeDocumentReq request = new UpdateKnowledgeDocumentReq()
                .setId(200L)
                .setTitle("renamed.pdf")
                .setChunkMode("FIXED")
                .setChunkConfig("{\"chunkSize\":512,\"chunkOverlap\":50}")
                .setVisibilityScope("SENSITIVE")
                .setMinRankLevel(10)
                .setAuthorizedDepartmentIds(java.util.List.of(21L, 22L));
        when(knowledgeDocumentMapper.selectOne(any(Wrapper.class))).thenReturn(existing);
        when(knowledgeDocumentMapper.updateById(any(KnowledgeDocumentDO.class))).thenReturn(1);

        KnowledgeDocumentDetailResp response = knowledgeDocumentService.updateKnowledgeDocument(request);

        assertThat(response.getAuthorizedDepartmentIds()).containsExactly(21L, 22L);
        verify(documentDepartmentAuthMapper).delete(any(Wrapper.class));
        ArgumentCaptor<DocumentDepartmentAuthDO> authCaptor = ArgumentCaptor.forClass(DocumentDepartmentAuthDO.class);
        verify(documentDepartmentAuthMapper, org.mockito.Mockito.times(2)).insert(authCaptor.capture());
        assertThat(authCaptor.getAllValues())
                .extracting(DocumentDepartmentAuthDO::getDocumentId, DocumentDepartmentAuthDO::getDepartmentId)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(200L, 21L),
                        org.assertj.core.groups.Tuple.tuple(200L, 22L));
    }

    @Test
    void shouldRejectUnknownVisibilityScopeWhenUpdatingDocument() {
        UserContext.set(LoginUser.builder().userId("10001").build());
        KnowledgeDocumentDO existing = existingDocument(ParseStatusEnum.SUCCESS.getCode());
        UpdateKnowledgeDocumentReq request = new UpdateKnowledgeDocumentReq()
                .setId(200L)
                .setTitle("renamed.pdf")
                .setChunkMode("FIXED")
                .setChunkConfig("{\"chunkSize\":512,\"chunkOverlap\":50}")
                .setVisibilityScope("PUBLIC")
                .setMinRankLevel(10);
        when(knowledgeDocumentMapper.selectOne(any(Wrapper.class))).thenReturn(existing);

        assertThatThrownBy(() -> knowledgeDocumentService.updateKnowledgeDocument(request))
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("visibilityScope");

        verify(knowledgeDocumentMapper, never()).updateById(any(KnowledgeDocumentDO.class));
        verify(documentDepartmentAuthMapper, never()).delete(any(Wrapper.class));
    }

    @Test
    void shouldRejectMissingVisibilityScopeWhenUpdatingDocument() {
        UserContext.set(LoginUser.builder().userId("10001").build());
        KnowledgeDocumentDO existing = existingDocument(ParseStatusEnum.SUCCESS.getCode());
        UpdateKnowledgeDocumentReq request = new UpdateKnowledgeDocumentReq()
                .setId(200L)
                .setTitle("renamed.pdf")
                .setChunkMode("FIXED")
                .setChunkConfig("{\"chunkSize\":512,\"chunkOverlap\":50}")
                .setMinRankLevel(10);
        when(knowledgeDocumentMapper.selectOne(any(Wrapper.class))).thenReturn(existing);

        assertThatThrownBy(() -> knowledgeDocumentService.updateKnowledgeDocument(request))
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("visibilityScope");

        verify(knowledgeDocumentMapper, never()).updateById(any(KnowledgeDocumentDO.class));
        verify(documentDepartmentAuthMapper, never()).delete(any(Wrapper.class));
    }

    @Test
    void shouldClearDepartmentAuthWhenUpdatingDocumentToInternal() {
        UserContext.set(LoginUser.builder().userId("10001").build());
        KnowledgeDocumentDO existing = existingDocument(ParseStatusEnum.SUCCESS.getCode());
        existing.setVisibilityScope("SENSITIVE");
        UpdateKnowledgeDocumentReq request = new UpdateKnowledgeDocumentReq()
                .setId(200L)
                .setTitle("renamed.pdf")
                .setChunkMode("FIXED")
                .setChunkConfig("{\"chunkSize\":512,\"chunkOverlap\":50}")
                .setVisibilityScope("INTERNAL")
                .setMinRankLevel(10)
                .setAuthorizedDepartmentIds(java.util.List.of(21L));
        when(knowledgeDocumentMapper.selectOne(any(Wrapper.class))).thenReturn(existing);
        when(knowledgeDocumentMapper.updateById(any(KnowledgeDocumentDO.class))).thenReturn(1);

        KnowledgeDocumentDetailResp response = knowledgeDocumentService.updateKnowledgeDocument(request);

        assertThat(response.getAuthorizedDepartmentIds()).isEmpty();
        verify(documentDepartmentAuthMapper).delete(any(Wrapper.class));
        verify(documentDepartmentAuthMapper, never()).insert(any(DocumentDepartmentAuthDO.class));
    }

    @Test
    void shouldReturnEmptyDepartmentAuthForInternalDocumentDetail() {
        KnowledgeDocumentDO existing = existingDocument(ParseStatusEnum.SUCCESS.getCode());
        existing.setVisibilityScope("INTERNAL");
        when(knowledgeDocumentMapper.selectOne(any(Wrapper.class))).thenReturn(existing);

        KnowledgeDocumentDetailResp response = knowledgeDocumentService.getKnowledgeDocument(200L);

        assertThat(response.getAuthorizedDepartmentIds()).isEmpty();
        verify(documentDepartmentAuthMapper, never()).selectList(any(Wrapper.class));
    }

    @Test
    void shouldReturnNullWhenDocumentDeleted() {
        KnowledgeDocumentDetailResp response = knowledgeDocumentService.getKnowledgeDocument(200L);

        assertThat(response).isNull();
    }

    @Test
    void shouldReplaceSensitiveDepartmentAuthWhenChunkConfigChanges() {
        UserContext.set(LoginUser.builder().userId("10001").build());
        KnowledgeDocumentDO existing = existingDocument(ParseStatusEnum.SUCCESS.getCode());
        existing.setVisibilityScope("SENSITIVE");
        UpdateKnowledgeDocumentReq request = new UpdateKnowledgeDocumentReq()
                .setId(200L)
                .setTitle("manual.pdf")
                .setChunkMode("STRUCTURE_AWARE")
                .setChunkConfig("{\"chunkSize\":800,\"chunkOverlap\":80}")
                .setVisibilityScope("SENSITIVE")
                .setMinRankLevel(10)
                .setAuthorizedDepartmentIds(java.util.List.of(31L, 32L));
        when(knowledgeDocumentMapper.selectOne(any(Wrapper.class))).thenReturn(existing);
        when(chunkMapper.selectList(any(Wrapper.class))).thenReturn(java.util.List.of());
        doAnswer(invocation -> {
            ((java.util.function.Consumer<Object>) invocation.getArgument(4)).accept(null);
            return null;
        }).when(messageQueueProducer).sendInTransaction(any(), any(), any(), any(), any());

        KnowledgeDocumentDetailResp response = knowledgeDocumentService.updateKnowledgeDocument(request);

        assertThat(response.getAuthorizedDepartmentIds()).containsExactly(31L, 32L);
        verify(documentDepartmentAuthMapper).delete(any(Wrapper.class));
        ArgumentCaptor<DocumentDepartmentAuthDO> authCaptor = ArgumentCaptor.forClass(DocumentDepartmentAuthDO.class);
        verify(documentDepartmentAuthMapper, org.mockito.Mockito.times(2)).insert(authCaptor.capture());
        assertThat(authCaptor.getAllValues())
                .extracting(DocumentDepartmentAuthDO::getDocumentId, DocumentDepartmentAuthDO::getDepartmentId)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(200L, 31L),
                        org.assertj.core.groups.Tuple.tuple(200L, 32L));
    }

    private KnowledgeDocumentDO existingDocument(String parseStatus) {
        KnowledgeDocumentDO documentDO = new KnowledgeDocumentDO();
        documentDO.setId(200L);
        documentDO.setKnowledgeBaseId(100L);
        documentDO.setTitle("manual.pdf");
        documentDO.setStorageBucket("kb-doc-01");
        documentDO.setStorageKey("doc-key.pdf");
        documentDO.setChunkMode("FIXED");
        documentDO.setChunkConfig("{\"chunkSize\":512,\"chunkOverlap\":50}");
        documentDO.setParseStatus(parseStatus);
        documentDO.setVisibilityScope("INTERNAL");
        documentDO.setMinRankLevel(10);
        documentDO.setDeleteFlag(DeleteFlagEnum.NORMAL.getCode());
        return documentDO;
    }
}
