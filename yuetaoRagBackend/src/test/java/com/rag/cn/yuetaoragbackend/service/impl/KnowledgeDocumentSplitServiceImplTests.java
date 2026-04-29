package com.rag.cn.yuetaoragbackend.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rag.cn.yuetaoragbackend.config.enums.DeleteFlagEnum;
import com.rag.cn.yuetaoragbackend.config.enums.ParseStatusEnum;
import com.rag.cn.yuetaoragbackend.dao.entity.KnowledgeDocumentDO;
import com.rag.cn.yuetaoragbackend.dao.mapper.KnowledgeDocumentMapper;
import com.rag.cn.yuetaoragbackend.service.DocumentChunkLogService;
import com.rag.cn.yuetaoragbackend.service.file.FileService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * @author zrq
 * 2026/04/27 10:40
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeDocumentSplitServiceImplTests {

    @Mock
    private KnowledgeDocumentMapper knowledgeDocumentMapper;

    @Mock
    private DocumentChunkLogService documentChunkLogService;

    @Mock
    private FileService fileService;

    @Mock
    private PgVectorStore chunkVectorStore;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private KnowledgeDocumentSplitServiceImpl splitExecutionService;

    @Test
    void shouldSplitFixedModeByCharactersAndOverlap() {
        KnowledgeDocumentDO documentDO = document("abcdefghij", "FIXED", "{\"chunkSize\":4,\"chunkOverlap\":1}");
        when(knowledgeDocumentMapper.selectOne(any())).thenReturn(documentDO);
        when(fileService.getObject("kb-doc-01", "doc-key.txt")).thenReturn("abcdefghij".getBytes(StandardCharsets.UTF_8));

        splitExecutionService.processSplit(200L, 900L);

        ArgumentCaptor<List<Object[]>> batchCaptor = ArgumentCaptor.forClass(List.class);
        verify(jdbcTemplate).batchUpdate(any(String.class), batchCaptor.capture());
        assertThat(batchCaptor.getValue()).hasSize(3);
        assertThat((String) batchCaptor.getValue().get(0)[5]).isEqualTo("abcd");
        assertThat((String) batchCaptor.getValue().get(1)[5]).isEqualTo("defg");
        assertThat((String) batchCaptor.getValue().get(2)[5]).isEqualTo("ghij");
        ArgumentCaptor<List<Document>> docsCaptor = ArgumentCaptor.forClass(List.class);
        verify(chunkVectorStore).add(docsCaptor.capture());
        assertThat(docsCaptor.getValue()).hasSize(3);
        assertThat(docsCaptor.getValue().get(0).getMetadata().get("document_id")).isEqualTo("200");

        verify(documentChunkLogService).recordSplitResult(eq(900L), eq(3), anyLong());
        verify(documentChunkLogService).recordVectorResult(eq(900L), anyLong());
        verify(documentChunkLogService).markSuccess(eq(900L), eq(3), anyLong(), anyLong(), anyLong());
    }

    @Test
    void shouldSplitStructureAwareByMarkdownHeading() {
        String markdown = "# Title 1\npara one\n## Title 2\npara two";
        KnowledgeDocumentDO documentDO = document(markdown, "STRUCTURE_AWARE", "{\"chunkSize\":100,\"chunkOverlap\":10}");
        when(knowledgeDocumentMapper.selectOne(any())).thenReturn(documentDO);
        when(fileService.getObject("kb-doc-01", "doc-key.txt")).thenReturn(markdown.getBytes(StandardCharsets.UTF_8));

        splitExecutionService.processSplit(200L, 900L);

        ArgumentCaptor<List<Object[]>> batchCaptor = ArgumentCaptor.forClass(List.class);
        verify(jdbcTemplate).batchUpdate(any(String.class), batchCaptor.capture());
        assertThat(batchCaptor.getValue()).hasSize(2);
        assertThat((String) batchCaptor.getValue().get(0)[5]).isEqualTo("# Title 1\npara one");
        assertThat((String) batchCaptor.getValue().get(1)[5]).isEqualTo("## Title 2\npara two");
    }

    @Test
    void shouldMarkDocumentAndChunkLogFailedWithErrorMessage() {
        splitExecutionService.markSplitFailed(200L, 900L, "vector service unavailable");

        verify(knowledgeDocumentMapper).updateById(org.mockito.ArgumentMatchers.<KnowledgeDocumentDO>argThat(document ->
                document.getId().equals(200L)
                        && ParseStatusEnum.FAILED.getCode().equals(document.getParseStatus())));
        verify(documentChunkLogService).markFailed(900L, "vector service unavailable");
    }

    @Test
    void shouldMarkLatestProcessingChunkLogTimeout() {
        splitExecutionService.markSplitTimeout(200L);

        verify(knowledgeDocumentMapper).updateById(org.mockito.ArgumentMatchers.<KnowledgeDocumentDO>argThat(document ->
                document.getId().equals(200L)
                        && ParseStatusEnum.FAILED.getCode().equals(document.getParseStatus())));
        verify(documentChunkLogService).markTimeout(200L);
    }

    @Test
    void shouldContinueProcessSplitWhenRecordSplitResultFails() {
        KnowledgeDocumentDO documentDO = document("abcdefghij", "FIXED", "{\"chunkSize\":4,\"chunkOverlap\":1}");
        when(knowledgeDocumentMapper.selectOne(any())).thenReturn(documentDO);
        when(fileService.getObject("kb-doc-01", "doc-key.txt")).thenReturn("abcdefghij".getBytes(StandardCharsets.UTF_8));
        doThrow(new RuntimeException("log unavailable")).when(documentChunkLogService)
                .recordSplitResult(eq(900L), eq(3), anyLong());

        splitExecutionService.processSplit(200L, 900L);

        verify(chunkVectorStore).add(any());
        verify(knowledgeDocumentMapper).updateById(org.mockito.ArgumentMatchers.<KnowledgeDocumentDO>argThat(document ->
                document.getId().equals(200L)
                        && ParseStatusEnum.SUCCESS.getCode().equals(document.getParseStatus())));
        verify(documentChunkLogService).markSuccess(eq(900L), eq(3), anyLong(), anyLong(), anyLong());
    }

    @Test
    void shouldContinueMarkSplitFailedWhenLogUpdateFails() {
        doThrow(new RuntimeException("log unavailable")).when(documentChunkLogService)
                .markFailed(900L, "vector service unavailable");

        splitExecutionService.markSplitFailed(200L, 900L, "vector service unavailable");

        verify(knowledgeDocumentMapper).updateById(org.mockito.ArgumentMatchers.<KnowledgeDocumentDO>argThat(document ->
                document.getId().equals(200L)
                        && ParseStatusEnum.FAILED.getCode().equals(document.getParseStatus())));
        verify(documentChunkLogService).markFailed(900L, "vector service unavailable");
    }

    @Test
    void shouldContinueMarkSplitTimeoutWhenLogUpdateFails() {
        doThrow(new RuntimeException("log unavailable")).when(documentChunkLogService).markTimeout(200L);

        splitExecutionService.markSplitTimeout(200L);

        verify(knowledgeDocumentMapper).updateById(org.mockito.ArgumentMatchers.<KnowledgeDocumentDO>argThat(document ->
                document.getId().equals(200L)
                        && ParseStatusEnum.FAILED.getCode().equals(document.getParseStatus())));
        verify(documentChunkLogService).markTimeout(200L);
    }

    private KnowledgeDocumentDO document(String text, String chunkMode, String chunkConfig) {
        KnowledgeDocumentDO documentDO = new KnowledgeDocumentDO();
        documentDO.setId(200L);
        documentDO.setKnowledgeBaseId(100L);
        documentDO.setTitle("doc-key.txt");
        documentDO.setStorageBucket("kb-doc-01");
        documentDO.setStorageKey("doc-key.txt");
        documentDO.setParseStatus("PROCESSING");
        documentDO.setChunkMode(chunkMode);
        documentDO.setChunkConfig(chunkConfig);
        documentDO.setUpdatedBy(10001L);
        documentDO.setDeleteFlag(DeleteFlagEnum.NORMAL.getCode());
        return documentDO;
    }
}
