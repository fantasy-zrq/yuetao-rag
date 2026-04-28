package com.rag.cn.yuetaoragbackend.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rag.cn.yuetaoragbackend.config.enums.DocumentChunkLogStatusEnum;
import com.rag.cn.yuetaoragbackend.dao.entity.DocumentChunkLogDO;
import com.rag.cn.yuetaoragbackend.dao.mapper.DocumentChunkLogMapper;
import java.lang.reflect.Method;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author zrq
 * 2026/04/28 22:00
 */
@ExtendWith(MockitoExtension.class)
class DocumentChunkLogServiceImplTests {

    @Mock
    private DocumentChunkLogMapper documentChunkLogMapper;

    @InjectMocks
    private DocumentChunkLogServiceImpl documentChunkLogService;

    @Test
    void shouldUpdateSplitAndVectorProgressIndependently() {
        documentChunkLogService.recordSplitResult(900L, 3, 120L);
        documentChunkLogService.recordVectorResult(900L, 80L);

        verify(documentChunkLogMapper).updateById(org.mockito.ArgumentMatchers.<DocumentChunkLogDO>argThat(log ->
                log.getId().equals(900L)
                        && Objects.equals(log.getChunkCount(), 3)
                        && Objects.equals(log.getSplitCostMillis(), 120L)));
        verify(documentChunkLogMapper).updateById(org.mockito.ArgumentMatchers.<DocumentChunkLogDO>argThat(log ->
                log.getId().equals(900L)
                        && Objects.equals(log.getVectorCostMillis(), 80L)));
    }

    @Test
    void shouldMarkTerminalStatesWithTotalCostAndEndTime() {
        DocumentChunkLogDO existingLog = new DocumentChunkLogDO();
        existingLog.setId(900L);
        existingLog.setStartTime(new Date(System.currentTimeMillis() - 1000L));
        when(documentChunkLogMapper.selectById(900L)).thenReturn(existingLog);

        documentChunkLogService.markFailed(900L, "vector service unavailable");

        verify(documentChunkLogMapper).updateById(org.mockito.ArgumentMatchers.<DocumentChunkLogDO>argThat(log ->
                log.getId().equals(900L)
                        && DocumentChunkLogStatusEnum.FAILED.getCode().equals(log.getStatus())
                        && log.getErrorMessage().contains("vector service unavailable")
                        && log.getTotalCostMillis() != null
                        && log.getEndTime() != null));
    }

    @Test
    void shouldMarkLatestProcessingLogTimeout() {
        DocumentChunkLogDO processingLog = new DocumentChunkLogDO();
        processingLog.setId(900L);
        processingLog.setDocumentId(200L);
        processingLog.setStatus(DocumentChunkLogStatusEnum.PROCESSING.getCode());
        processingLog.setStartTime(new Date(System.currentTimeMillis() - 15 * 60 * 1000L));
        when(documentChunkLogMapper.selectList(any())).thenReturn(List.of(processingLog));
        when(documentChunkLogMapper.selectById(900L)).thenReturn(processingLog);

        documentChunkLogService.markTimeout(200L);

        verify(documentChunkLogMapper).updateById(org.mockito.ArgumentMatchers.<DocumentChunkLogDO>argThat(log ->
                log.getId().equals(900L)
                        && DocumentChunkLogStatusEnum.TIMEOUT.getCode().equals(log.getStatus())
                        && log.getErrorMessage().contains("切片超时")
                        && log.getTotalCostMillis() != null
                        && log.getEndTime() != null));
    }

    @Test
    void shouldUseRequiresNewTransactionsForAllLogUpdates() throws Exception {
        assertRequiresNew("recordSplitResult", Long.class, int.class, long.class);
        assertRequiresNew("recordVectorResult", Long.class, long.class);
        assertRequiresNew("markSuccess", Long.class, int.class, long.class, long.class, long.class);
        assertRequiresNew("markFailed", Long.class, String.class);
        assertRequiresNew("markTimeout", Long.class);
    }

    private void assertRequiresNew(String methodName, Class<?>... parameterTypes) throws Exception {
        Method method = DocumentChunkLogServiceImpl.class.getMethod(methodName, parameterTypes);
        Transactional transactional = AnnotatedElementUtils.findMergedAnnotation(method, Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }
}
