package com.rag.cn.yuetaoragbackend.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rag.cn.yuetaoragbackend.config.record.ChatModelInfoRecord;
import com.rag.cn.yuetaoragbackend.config.enums.ChatSessionStatusEnum;
import com.rag.cn.yuetaoragbackend.config.enums.DeleteFlagEnum;
import com.rag.cn.yuetaoragbackend.config.properties.MemoryProperties;
import com.rag.cn.yuetaoragbackend.config.properties.TraceProperties;
import com.rag.cn.yuetaoragbackend.dao.entity.ChatMessageDO;
import com.rag.cn.yuetaoragbackend.dao.entity.ChatSessionDO;
import com.rag.cn.yuetaoragbackend.dao.entity.UserDO;
import com.rag.cn.yuetaoragbackend.dao.mapper.ChatMessageMapper;
import com.rag.cn.yuetaoragbackend.dao.mapper.ChatSessionMapper;
import com.rag.cn.yuetaoragbackend.dao.mapper.QaTraceLogMapper;
import com.rag.cn.yuetaoragbackend.dao.mapper.UserMapper;
import com.rag.cn.yuetaoragbackend.dto.req.ChatReq;
import com.rag.cn.yuetaoragbackend.dto.resp.ChatResp;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * @author zrq
 * 2026/04/29 16:10
 */
@ExtendWith(MockitoExtension.class)
class ChatMessageServiceImplTests {

    @Mock
    private ChatMessageMapper chatMessageMapper;

    @Mock
    private ChatSessionMapper chatSessionMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private QaTraceLogMapper qaTraceLogMapper;

    @Mock
    private ChatModelGateway chatModelGateway;

    @Mock
    private RagRetrievalService ragRetrievalService;

    private MemoryProperties memoryProperties;

    private TraceProperties traceProperties;

    @InjectMocks
    private ChatMessageServiceImpl chatMessageService;

    @BeforeEach
    void setUp() {
        memoryProperties = new MemoryProperties();
        memoryProperties.setRecentWindowSize(12);
        traceProperties = new TraceProperties();
        traceProperties.setEnabled(true);
        traceProperties.setLogPayload(true);
        chatMessageService = new ChatMessageServiceImpl(
                chatMessageMapper,
                chatSessionMapper,
                userMapper,
                qaTraceLogMapper,
                chatModelGateway,
                ragRetrievalService,
                memoryProperties,
                traceProperties);
    }

    @Test
    void shouldChatWithoutKnowledgeRetrievalForChitchatIntent() {
        when(chatSessionMapper.selectById(10L)).thenReturn(session());
        when(userMapper.selectById(20L)).thenReturn(user());
        when(chatMessageMapper.selectList(any())).thenReturn(List.of());
        when(chatModelGateway.classifyQuestionIntent("你好", List.of())).thenReturn("CHITCHAT");
        when(chatModelGateway.generateChitchatAnswer("你好", List.of()))
                .thenReturn("你好，请问有什么可以帮你？");
        when(chatModelGateway.currentModelInfo()).thenReturn(new ChatModelInfoRecord("bailian", "qwen-plus"));

        ChatResp response = chatMessageService.chat(new ChatReq()
                .setSessionId(10L)
                .setUserId(20L)
                .setMessage("你好"));

        assertThat(response.getIntentType()).isEqualTo("CHITCHAT");
        assertThat(response.getKnowledgeHit()).isFalse();
        assertThat(response.getCitations()).isEmpty();
        assertThat(response.getAnswer()).contains("你好");
        verify(ragRetrievalService, never()).retrieve(any(), any());
        verify(ragRetrievalService, never()).rerank(any(String.class), any(List.class));
        verify(chatMessageMapper, times(2)).insert(any(ChatMessageDO.class));
    }

    @Test
    void shouldChatWithKnowledgeCitationsForKnowledgeIntent() {
        when(chatSessionMapper.selectById(10L)).thenReturn(session());
        when(userMapper.selectById(20L)).thenReturn(user());
        when(chatMessageMapper.selectList(any())).thenReturn(List.of());
        when(chatModelGateway.classifyQuestionIntent("商品退货规则是什么", List.of())).thenReturn("KB_QA");
        when(chatModelGateway.rewriteQuestion("商品退货规则是什么", List.of())).thenReturn("商品退货规则");
        List<RagRetrievalService.RetrievedChunk> recalledChunks = List.of(
                new RagRetrievalService.RetrievedChunk(101L, 201L, "商品退货规则", 3,
                        "商品支持7天无理由退货，特殊品类除外。", 0.82D, 0D, 0D));
        List<RagRetrievalService.RetrievedChunk> rerankedChunks = List.of(
                new RagRetrievalService.RetrievedChunk(101L, 201L, "商品退货规则", 3,
                        "商品支持7天无理由退货，特殊品类除外。", 0.82D, 0.50D, 0.71D));
        when(ragRetrievalService.retrieve(any(UserDO.class), any(String.class))).thenReturn(recalledChunks);
        when(ragRetrievalService.rerank(any(String.class), any(List.class))).thenReturn(rerankedChunks);
        when(chatModelGateway.generateAnswer(any(String.class), any(String.class), any(List.class), any(List.class)))
                .thenReturn("商品支持7天无理由退货[1]。");
        when(chatModelGateway.currentModelInfo()).thenReturn(new ChatModelInfoRecord("bailian", "qwen-plus"));

        ChatResp response = chatMessageService.chat(new ChatReq()
                .setSessionId(10L)
                .setUserId(20L)
                .setMessage("商品退货规则是什么"));

        assertThat(response.getIntentType()).isEqualTo("KB_QA");
        assertThat(response.getKnowledgeHit()).isTrue();
        assertThat(response.getCitations()).hasSize(1);
        assertThat(response.getCitations().get(0).getReferenceLabel()).isEqualTo("商品退货规则（切片#3）");
        assertThat(response.getAnswer()).contains("[1]");
        verify(ragRetrievalService).retrieve(any(UserDO.class), any(String.class));
        verify(ragRetrievalService).rerank(any(String.class), any(List.class));
    }

    @Test
    void shouldRefuseAnswerWhenRerankResultsEmpty() {
        when(chatSessionMapper.selectById(10L)).thenReturn(session());
        when(userMapper.selectById(20L)).thenReturn(user());
        when(chatMessageMapper.selectList(any())).thenReturn(List.of());
        when(chatModelGateway.classifyQuestionIntent("报销流程是什么", List.of())).thenReturn("KB_QA");
        when(chatModelGateway.rewriteQuestion("报销流程是什么", List.of())).thenReturn("报销流程");
        when(ragRetrievalService.retrieve(any(UserDO.class), any(String.class))).thenReturn(List.of());
        when(ragRetrievalService.rerank(any(String.class), any(List.class))).thenReturn(List.of());
        when(chatModelGateway.currentModelInfo()).thenReturn(new ChatModelInfoRecord("bailian", "qwen-plus"));

        ChatResp response = chatMessageService.chat(new ChatReq()
                .setSessionId(10L)
                .setUserId(20L)
                .setMessage("报销流程是什么"));

        assertThat(response.getIntentType()).isEqualTo("KB_QA");
        assertThat(response.getKnowledgeHit()).isFalse();
        assertThat(response.getAnswer()).isEqualTo("当前知识库中没有该方面的内容，暂时无法回答这个问题。");
        assertThat(response.getCitations()).isEmpty();
        verify(chatModelGateway, never()).generateAnswer(any(String.class), any(String.class), any(List.class), any(List.class));
    }

    private ChatSessionDO session() {
        ChatSessionDO sessionDO = new ChatSessionDO();
        sessionDO.setId(10L);
        sessionDO.setUserId(20L);
        sessionDO.setStatus(ChatSessionStatusEnum.ACTIVE.getCode());
        sessionDO.setDeleteFlag(DeleteFlagEnum.NORMAL.getCode());
        return sessionDO;
    }

    private UserDO user() {
        UserDO userDO = new UserDO();
        userDO.setId(20L);
        userDO.setRankLevel(10);
        userDO.setRoleCode("USER");
        userDO.setDeleteFlag(DeleteFlagEnum.NORMAL.getCode());
        return userDO;
    }
}
