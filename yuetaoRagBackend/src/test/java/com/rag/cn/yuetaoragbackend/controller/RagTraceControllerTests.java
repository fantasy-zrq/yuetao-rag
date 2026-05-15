package com.rag.cn.yuetaoragbackend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rag.cn.yuetaoragbackend.dto.req.PageRunsReq;
import com.rag.cn.yuetaoragbackend.dto.resp.PageResp;
import com.rag.cn.yuetaoragbackend.dto.resp.RagTraceDetailResp;
import com.rag.cn.yuetaoragbackend.dto.resp.RagTraceNodeResp;
import com.rag.cn.yuetaoragbackend.dto.resp.RagTraceRunResp;
import java.util.Map;
import com.rag.cn.yuetaoragbackend.service.RagTraceService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class RagTraceControllerTests {

    @Mock
    private RagTraceService ragTraceService;

    @Test
    void shouldBindPageRunsFromQueryParametersOnGet() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new RagTraceController(ragTraceService)).build();
        when(ragTraceService.pageRuns(any(PageRunsReq.class))).thenReturn(new PageResp<RagTraceRunResp>()
                .setRecords(List.of())
                .setTotal(0L)
                .setSize(10L)
                .setCurrent(1L)
                .setPages(0L));

        mockMvc.perform(MockMvcRequestBuilders.get("/rag/traces/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .param("current", "2")
                        .param("size", "5")
                        .param("traceId", "trace-abc"))
                .andExpect(status().isOk());

        ArgumentCaptor<PageRunsReq> captor = ArgumentCaptor.forClass(PageRunsReq.class);
        verify(ragTraceService).pageRuns(captor.capture());
        PageRunsReq requestParam = captor.getValue();
        org.assertj.core.api.Assertions.assertThat(requestParam.getCurrent()).isEqualTo(2L);
        org.assertj.core.api.Assertions.assertThat(requestParam.getSize()).isEqualTo(5L);
        org.assertj.core.api.Assertions.assertThat(requestParam.getTraceId()).isEqualTo("trace-abc");
    }

    @Test
    void shouldReturnStructuredTraceNodeDetails() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new RagTraceController(ragTraceService)).build();
        when(ragTraceService.detail("trace-abc")).thenReturn(new RagTraceDetailResp()
                .setRun(new RagTraceRunResp()
                        .setTraceId("trace-abc")
                        .setTraceName("trace-abc")
                        .setStatus("SUCCESS"))
                .setNodes(List.of(new RagTraceNodeResp()
                        .setTraceId("trace-abc")
                        .setNodeId("REWRITE")
                        .setNodeType("REWRITE")
                        .setNodeName("REWRITE")
                        .setPayloadRef("{\"originalQuestion\":\"开题报告怎么写\"}")
                        .setDetails(Map.of("originalQuestion", "开题报告怎么写")))));

        mockMvc.perform(MockMvcRequestBuilders.get("/rag/traces/runs/trace-abc")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.run.traceId").value("trace-abc"))
                .andExpect(jsonPath("$.data.nodes[0].payloadRef").value("{\"originalQuestion\":\"开题报告怎么写\"}"))
                .andExpect(jsonPath("$.data.nodes[0].details.originalQuestion").value("开题报告怎么写"));

        verify(ragTraceService).detail("trace-abc");
    }
}
