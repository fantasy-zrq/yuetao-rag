package com.rag.cn.yuetaoragbackend.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.cn.yuetaoragbackend.dao.mapper.QaTraceLogMapper;
import com.rag.cn.yuetaoragbackend.dto.resp.RagTraceDetailResp;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RagTraceServiceImplTests {

    @Mock
    private QaTraceLogMapper qaTraceLogMapper;

    @InjectMocks
    private RagTraceServiceImpl ragTraceService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldParseStructuredNodePayloadAsDetails() {
        ragTraceService = new RagTraceServiceImpl(qaTraceLogMapper, objectMapper);
        Date startTime = new Date();
        when(qaTraceLogMapper.selectDetailByTraceId("trace-1")).thenReturn(Map.of(
                "trace_id", "trace-1",
                "start_time", startTime,
                "end_time", new Date(startTime.getTime() + 30L),
                "session_id", 10L,
                "user_id", 20L,
                "status", "SUCCESS",
                "duration_ms", 30L));
        when(qaTraceLogMapper.selectNodesByTraceId("trace-1")).thenReturn(List.of(Map.of(
                "trace_id", "trace-1",
                "stage", "REWRITE",
                "status", "SUCCESS",
                "latency_ms", 12L,
                "payload_ref", "{\"originalQuestion\":\"开题报告怎么写\",\"rewrittenQuestion\":\"开题报告怎么写模板\"}",
                "create_time", startTime,
                "next_create_time", new Date(startTime.getTime() + 12L),
                "id", 1L)));

        RagTraceDetailResp detail = ragTraceService.detail("trace-1");

        assertThat(detail.getNodes()).hasSize(1);
        assertThat(detail.getNodes().get(0).getPayloadRef())
                .isEqualTo("{\"originalQuestion\":\"开题报告怎么写\",\"rewrittenQuestion\":\"开题报告怎么写模板\"}");
        assertThat(detail.getNodes().get(0).getDetails())
                .containsEntry("originalQuestion", "开题报告怎么写")
                .containsEntry("rewrittenQuestion", "开题报告怎么写模板");
    }

    @Test
    void shouldFallbackToLegacyKeyValuePayloadDetails() {
        ragTraceService = new RagTraceServiceImpl(qaTraceLogMapper, objectMapper);
        Date startTime = new Date();
        when(qaTraceLogMapper.selectDetailByTraceId("trace-2")).thenReturn(Map.of(
                "trace_id", "trace-2",
                "start_time", startTime,
                "end_time", new Date(startTime.getTime() + 20L),
                "session_id", 10L,
                "user_id", 20L,
                "status", "SUCCESS",
                "duration_ms", 20L));
        when(qaTraceLogMapper.selectNodesByTraceId("trace-2")).thenReturn(List.of(Map.of(
                "trace_id", "trace-2",
                "stage", "RETRIEVE",
                "status", "SUCCESS",
                "latency_ms", 8L,
                "payload_ref", "candidateCount=2,kbIds=[301, 302]",
                "create_time", startTime,
                "next_create_time", new Date(startTime.getTime() + 8L),
                "id", 2L)));

        RagTraceDetailResp detail = ragTraceService.detail("trace-2");

        assertThat(detail.getNodes()).hasSize(1);
        assertThat(detail.getNodes().get(0).getDetails())
                .containsEntry("candidateCount", 2L)
                .containsEntry("kbIds", "[301, 302]")
                .containsEntry("scope", "KB_SCOPED")
                .containsEntry("knowledgeBaseIds", List.of("301", "302"));
    }

    @Test
    void shouldNormalizeLegacyIntentPayloadKeys() {
        ragTraceService = new RagTraceServiceImpl(qaTraceLogMapper, objectMapper);
        Date startTime = new Date();
        when(qaTraceLogMapper.selectDetailByTraceId("trace-3")).thenReturn(Map.of(
                "trace_id", "trace-3",
                "start_time", startTime,
                "end_time", new Date(startTime.getTime() + 10L),
                "session_id", 10L,
                "user_id", 20L,
                "status", "SUCCESS",
                "duration_ms", 10L));
        when(qaTraceLogMapper.selectNodesByTraceId("trace-3")).thenReturn(List.of(Map.of(
                "trace_id", "trace-3",
                "stage", "INTENT",
                "status", "SUCCESS",
                "latency_ms", 5L,
                "payload_ref", "intent=KB_QA,fallback=true",
                "create_time", startTime,
                "next_create_time", new Date(startTime.getTime() + 5L),
                "id", 3L)));

        RagTraceDetailResp detail = ragTraceService.detail("trace-3");

        assertThat(detail.getNodes()).hasSize(1);
        assertThat(detail.getNodes().get(0).getDetails())
                .containsEntry("resolvedIntentType", "KB_QA")
                .containsEntry("fallbackModel", true)
                .containsEntry("routeSource", "MODEL_CLASSIFIER");
    }
}
