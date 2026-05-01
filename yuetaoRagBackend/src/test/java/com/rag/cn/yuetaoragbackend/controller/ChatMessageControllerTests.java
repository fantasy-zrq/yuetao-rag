package com.rag.cn.yuetaoragbackend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.rag.cn.yuetaoragbackend.config.enums.ChatSessionStatusEnum;
import com.rag.cn.yuetaoragbackend.config.enums.DeleteFlagEnum;
import com.rag.cn.yuetaoragbackend.config.record.ChatModelInfoRecord;
import com.rag.cn.yuetaoragbackend.dao.entity.ChatMessageDO;
import com.rag.cn.yuetaoragbackend.dao.entity.ChatSessionDO;
import com.rag.cn.yuetaoragbackend.dao.entity.QaTraceLogDO;
import com.rag.cn.yuetaoragbackend.dao.entity.UserDO;
import com.rag.cn.yuetaoragbackend.dao.mapper.ChatMessageMapper;
import com.rag.cn.yuetaoragbackend.dao.mapper.ChatSessionMapper;
import com.rag.cn.yuetaoragbackend.dao.mapper.QaTraceLogMapper;
import com.rag.cn.yuetaoragbackend.dao.mapper.UserMapper;
import com.rag.cn.yuetaoragbackend.service.impl.ChatModelGateway;
import com.rag.cn.yuetaoragbackend.service.impl.RagRetrievalService;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import reactor.core.publisher.Flux;

/**
 * @author zrq
 * 2026/04/29 20:10
 */
@SpringBootTest(properties = {
        "app.ai.providers.bailian.url=https://dashscope.aliyuncs.com",
        "app.ai.providers.bailian.api-key=test-bailian-key",
        "app.ai.providers.bailian.endpoints.chat=/compatible-mode/v1/chat/completions",
        "app.ai.providers.bailian.endpoints.embedding=/compatible-mode/v1/embeddings",
        "app.ai.providers.bailian.endpoints.rerank=/api/v1/services/rerank/text-rerank/text-rerank",
        "app.ai.chat.default-model=qwen-plus",
        "app.ai.chat.candidates[0].id=qwen-plus",
        "app.ai.chat.candidates[0].provider=bailian",
        "app.ai.chat.candidates[0].enabled=true",
        "app.ai.chat.candidates[0].model=qwen-plus",
        "app.ai.chat.candidates[0].priority=100",
        "app.ai.rerank.default-model=qwen3-vl-rerank",
        "app.ai.rerank.candidates[0].id=qwen3-vl-rerank",
        "app.ai.rerank.candidates[0].provider=bailian",
        "app.ai.rerank.candidates[0].model=qwen3-vl-rerank",
        "app.ai.rerank.candidates[0].priority=100",
        "app.ai.embedding.default-model=text-embedding-v4",
        "app.ai.embedding.candidates[0].id=text-embedding-v4",
        "app.ai.embedding.candidates[0].provider=bailian",
        "app.ai.embedding.candidates[0].model=text-embedding-v4",
        "app.ai.embedding.candidates[0].dimension=1024"
})
@AutoConfigureMockMvc
class ChatMessageControllerTests {

    private static final AtomicLong ID_SEQUENCE = new AtomicLong(910000000000000000L);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private ChatSessionMapper chatSessionMapper;

    @Autowired
    private ChatMessageMapper chatMessageMapper;

    @Autowired
    private QaTraceLogMapper qaTraceLogMapper;

    @MockBean
    private ChatModelGateway chatModelGateway;

    @MockBean
    private RagRetrievalService ragRetrievalService;

    private final List<Long> userIds = new ArrayList<>();
    private final List<Long> sessionIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (Long sessionId : sessionIds) {
            chatMessageMapper.delete(Wrappers.<ChatMessageDO>lambdaQuery()
                    .eq(ChatMessageDO::getSessionId, sessionId));
            qaTraceLogMapper.delete(Wrappers.<QaTraceLogDO>lambdaQuery()
                    .eq(QaTraceLogDO::getSessionId, sessionId));
        }
        sessionIds.forEach(chatSessionMapper::deleteById);
        userIds.forEach(userMapper::deleteById);
        sessionIds.clear();
        userIds.clear();
    }

    @Test
    void shouldChatThroughControllerForChitchat() throws Exception {
        UserDO user = persistUser("USER");
        ChatSessionDO session = persistSession(user.getId(), ChatSessionStatusEnum.ACTIVE.getCode());
        when(chatModelGateway.classifyQuestionIntent("你好", List.of())).thenReturn("CHITCHAT");
        when(chatModelGateway.generateChitchatAnswer("你好", List.of())).thenReturn("你好，请问有什么可以帮你？");
        when(chatModelGateway.currentModelInfo()).thenReturn(new ChatModelInfoRecord("bailian", "qwen-plus"));

        MvcResult mvcResult = mockMvc.perform(MockMvcRequestBuilders.post("/chat-messages/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": %d,
                                  "userId": %d,
                                  "message": "你好"
                                }
                                """.formatted(session.getId(), user.getId())))
                .andReturn();

        JsonNode json = objectMapper.readTree(mvcResult.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(json.path("code").asText()).isEqualTo("0");
        assertThat(json.path("data").path("intentType").asText()).isEqualTo("CHITCHAT");
        assertThat(json.path("data").path("knowledgeHit").asBoolean()).isFalse();
        assertThat(json.path("data").path("answer").asText()).contains("你好");
        assertThat(countMessages(session.getId())).isEqualTo(2);
        assertThat(countTraceLogs(session.getId())).isEqualTo(2);
    }

    @Test
    void shouldChatThroughControllerForKnowledgeQuestion() throws Exception {
        UserDO user = persistUser("USER");
        ChatSessionDO session = persistSession(user.getId(), ChatSessionStatusEnum.ACTIVE.getCode());
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

        MvcResult mvcResult = mockMvc.perform(MockMvcRequestBuilders.post("/chat-messages/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": %d,
                                  "userId": %d,
                                  "message": "商品退货规则是什么"
                                }
                                """.formatted(session.getId(), user.getId())))
                .andReturn();

        JsonNode json = objectMapper.readTree(mvcResult.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(json.path("code").asText()).isEqualTo("0");
        assertThat(json.path("data").path("intentType").asText()).isEqualTo("KB_QA");
        assertThat(json.path("data").path("knowledgeHit").asBoolean()).isTrue();
        assertThat(json.path("data").path("citations")).hasSize(1);
        assertThat(json.path("data").path("citations").get(0).path("referenceLabel").asText())
                .isEqualTo("商品退货规则（切片#3）");
        assertThat(countMessages(session.getId())).isEqualTo(2);
        assertThat(countTraceLogs(session.getId())).isEqualTo(5);
    }

    @Test
    void shouldReturnRefusalWhenRerankResultsEmpty() throws Exception {
        UserDO user = persistUser("USER");
        ChatSessionDO session = persistSession(user.getId(), ChatSessionStatusEnum.ACTIVE.getCode());
        when(chatModelGateway.classifyQuestionIntent("报销流程是什么", List.of())).thenReturn("KB_QA");
        when(chatModelGateway.rewriteQuestion("报销流程是什么", List.of())).thenReturn("报销流程");
        when(ragRetrievalService.retrieve(any(UserDO.class), any(String.class))).thenReturn(List.of());
        when(ragRetrievalService.rerank(any(String.class), any(List.class))).thenReturn(List.of());
        when(chatModelGateway.currentModelInfo()).thenReturn(new ChatModelInfoRecord("bailian", "qwen-plus"));

        MvcResult mvcResult = mockMvc.perform(MockMvcRequestBuilders.post("/chat-messages/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": %d,
                                  "userId": %d,
                                  "message": "报销流程是什么"
                                }
                                """.formatted(session.getId(), user.getId())))
                .andReturn();

        JsonNode json = objectMapper.readTree(mvcResult.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(json.path("code").asText()).isEqualTo("0");
        assertThat(json.path("data").path("knowledgeHit").asBoolean()).isFalse();
        assertThat(json.path("data").path("answer").asText()).isEqualTo("当前知识库中没有该方面的内容，暂时无法回答这个问题。");
        assertThat(countMessages(session.getId())).isEqualTo(2);
        assertThat(countTraceLogs(session.getId())).isEqualTo(5);
    }

    @Test
    void shouldRejectWhenRequestMissingRequiredFields() throws Exception {
        MvcResult mvcResult = mockMvc.perform(MockMvcRequestBuilders.post("/chat-messages/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": 1,
                                  "userId": 2,
                                  "message": "   "
                                }
                                """))
                .andReturn();

        JsonNode json = objectMapper.readTree(mvcResult.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(json.path("code").asText()).isEqualTo("A000001");
        assertThat(json.path("message").asText()).contains("会话ID、用户ID和消息内容不能为空");
    }

    @Test
    void shouldRejectWhenSessionNotExists() throws Exception {
        UserDO user = persistUser("USER");

        MvcResult mvcResult = mockMvc.perform(MockMvcRequestBuilders.post("/chat-messages/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": 999999,
                                  "userId": %d,
                                  "message": "你好"
                                }
                                """.formatted(user.getId())))
                .andReturn();

        JsonNode json = objectMapper.readTree(mvcResult.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(json.path("code").asText()).isEqualTo("A000001");
        assertThat(json.path("message").asText()).contains("会话不存在");
    }

    @Test
    void shouldRejectWhenSessionBelongsToAnotherUser() throws Exception {
        UserDO owner = persistUser("USER");
        UserDO other = persistUser("USER");
        ChatSessionDO session = persistSession(owner.getId(), ChatSessionStatusEnum.ACTIVE.getCode());

        MvcResult mvcResult = mockMvc.perform(MockMvcRequestBuilders.post("/chat-messages/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": %d,
                                  "userId": %d,
                                  "message": "你好"
                                }
                                """.formatted(session.getId(), other.getId())))
                .andReturn();

        JsonNode json = objectMapper.readTree(mvcResult.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(json.path("code").asText()).isEqualTo("A000001");
        assertThat(json.path("message").asText()).contains("无权访问该会话");
    }

    @Test
    void shouldRejectWhenSessionInactive() throws Exception {
        UserDO user = persistUser("USER");
        ChatSessionDO session = persistSession(user.getId(), ChatSessionStatusEnum.CLOSED.getCode());

        MvcResult mvcResult = mockMvc.perform(MockMvcRequestBuilders.post("/chat-messages/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": %d,
                                  "userId": %d,
                                  "message": "你好"
                                }
                                """.formatted(session.getId(), user.getId())))
                .andReturn();

        JsonNode json = objectMapper.readTree(mvcResult.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(json.path("code").asText()).isEqualTo("A000001");
        assertThat(json.path("message").asText()).contains("当前会话不可继续提问");
    }

    @Test
    void shouldRejectWhenUserNotExists() throws Exception {
        UserDO user = persistUser("USER");
        ChatSessionDO session = persistSession(user.getId(), ChatSessionStatusEnum.ACTIVE.getCode());
        userMapper.deleteById(user.getId());

        MvcResult mvcResult = mockMvc.perform(MockMvcRequestBuilders.post("/chat-messages/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": %d,
                                  "userId": %d,
                                  "message": "你好"
                                }
                                """.formatted(session.getId(), user.getId())))
                .andReturn();

        JsonNode json = objectMapper.readTree(mvcResult.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(json.path("code").asText()).isEqualTo("A000001");
        assertThat(json.path("message").asText()).contains("用户不存在");
    }

    @Test
    void shouldReturnServiceErrorWhenGatewayThrowsUnexpectedException() throws Exception {
        UserDO user = persistUser("USER");
        ChatSessionDO session = persistSession(user.getId(), ChatSessionStatusEnum.ACTIVE.getCode());
        when(chatModelGateway.classifyQuestionIntent("你好", List.of()))
                .thenThrow(new RuntimeException("gateway down"));

        MvcResult mvcResult = mockMvc.perform(MockMvcRequestBuilders.post("/chat-messages/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": %d,
                                  "userId": %d,
                                  "message": "你好"
                                }
                                """.formatted(session.getId(), user.getId())))
                .andReturn();

        JsonNode json = objectMapper.readTree(mvcResult.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(json.path("code").asText()).isEqualTo("B000001");
    }

    @Test
    void shouldReturnSseStreamForChatstream() throws Exception {
        UserDO user = persistUser("USER");
        ChatSessionDO session = persistSession(user.getId(), ChatSessionStatusEnum.ACTIVE.getCode());
        when(chatModelGateway.classifyQuestionIntent("你好", List.of())).thenReturn("CHITCHAT");
        when(chatModelGateway.streamingCandidateIds()).thenReturn(List.of("qwen-plus"));
        when(chatModelGateway.tryAcquireStreamingCandidate("qwen-plus")).thenReturn(true);
        when(chatModelGateway.streamChitchatByCandidate("qwen-plus", "你好", List.of()))
                .thenReturn(Flux.just("你", "好"));
        when(chatModelGateway.candidateInfo("qwen-plus")).thenReturn(new ChatModelInfoRecord("bailian", "qwen-plus"));

        MvcResult mvcResult = mockMvc.perform(MockMvcRequestBuilders.post("/chat-messages/chatstream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": %d,
                                  "userId": %d,
                                  "message": "你好"
                                }
                                """.formatted(session.getId(), user.getId())))
                .andExpect(request().asyncStarted())
                .andReturn();

        String responseBody = awaitResponseContains(mvcResult, "event:message_end");
        assertThat(mvcResult.getResponse().getContentType()).contains("text/event-stream");
        assertThat(responseBody).contains("event:message_start");
        assertThat(responseBody).contains("event:delta");
        assertThat(responseBody).contains("event:message_end");
    }

    @Test
    void shouldCompleteKnowledgeChatstreamWithResetAndPersistOnlyFinalAssistant() throws Exception {
        UserDO user = persistUser("USER");
        ChatSessionDO session = persistSession(user.getId(), ChatSessionStatusEnum.ACTIVE.getCode());
        String question = "商品退货规则是什么";
        List<RagRetrievalService.RetrievedChunk> recalledChunks = List.of(
                new RagRetrievalService.RetrievedChunk(101L, 201L, "商品退货规则", 3,
                        "商品支持7天无理由退货，特殊品类除外。", 0.82D, 0D, 0D));
        List<RagRetrievalService.RetrievedChunk> rerankedChunks = List.of(
                new RagRetrievalService.RetrievedChunk(101L, 201L, "商品退货规则", 3,
                        "商品支持7天无理由退货，特殊品类除外。", 0.82D, 0.50D, 0.71D));
        when(chatModelGateway.classifyQuestionIntent(question, List.of())).thenReturn("KB_QA");
        when(chatModelGateway.rewriteQuestion(question, List.of())).thenReturn("商品退货规则");
        when(ragRetrievalService.retrieve(any(UserDO.class), any(String.class))).thenReturn(recalledChunks);
        when(ragRetrievalService.rerank(any(String.class), any(List.class))).thenReturn(rerankedChunks);
        when(chatModelGateway.streamingCandidateIds()).thenReturn(List.of("qwen-plus", "glm-4.7"));
        when(chatModelGateway.tryAcquireStreamingCandidate("qwen-plus")).thenReturn(true);
        when(chatModelGateway.tryAcquireStreamingCandidate("glm-4.7")).thenReturn(true);
        when(chatModelGateway.streamKnowledgeAnswerByCandidate("qwen-plus", question, "商品退货规则", List.of(), rerankedChunks))
                .thenReturn(Flux.concat(Flux.just("错误半截"), Flux.error(new RuntimeException("model connection lost"))));
        when(chatModelGateway.streamKnowledgeAnswerByCandidate("glm-4.7", question, "商品退货规则", List.of(), rerankedChunks))
                .thenReturn(Flux.just("商品支持7天无理由退货", "，特殊品类除外。"));
        when(chatModelGateway.candidateInfo("glm-4.7")).thenReturn(new ChatModelInfoRecord("siliconflow", "GLM-4.7"));

        MvcResult mvcResult = mockMvc.perform(MockMvcRequestBuilders.post("/chat-messages/chatstream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": %d,
                                  "userId": %d,
                                  "message": "%s",
                                  "traceId": "trace-real-user-reset"
                                }
                                """.formatted(session.getId(), user.getId(), question)))
                .andExpect(request().asyncStarted())
                .andReturn();

        String responseBody = awaitResponseContains(mvcResult, "event:message_end");
        assertThat(responseBody).contains("event:message_start");
        assertThat(responseBody).contains("event:delta");
        assertThat(responseBody).contains("event:reset");
        assertThat(responseBody).contains("event:citation");
        assertThat(responseBody).contains("event:message_end");
        assertThat(responseBody.indexOf("event:reset")).isLessThan(responseBody.lastIndexOf("event:message_start"));
        assertThat(responseBody).contains("qwen-plus");
        assertThat(responseBody).contains("glm-4.7");

        List<ChatMessageDO> messages = listMessages(session.getId());
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).getRole()).isEqualTo("USER");
        assertThat(messages.get(0).getContent()).isEqualTo(question);
        assertThat(messages.get(1).getRole()).isEqualTo("ASSISTANT");
        assertThat(messages.get(1).getContent()).isEqualTo("商品支持7天无理由退货，特殊品类除外。");
        assertThat(messages.get(1).getContent()).doesNotContain("错误半截");
        assertThat(messages.get(1).getModelName()).isEqualTo("GLM-4.7");

        List<QaTraceLogDO> traces = listTraceLogs(session.getId());
        assertThat(traces).anySatisfy(each -> {
            assertThat(each.getStage()).isEqualTo("STREAM_CANDIDATE");
            assertThat(each.getStatus()).isEqualTo("FAILED");
        });
        assertThat(traces).anySatisfy(each -> {
            assertThat(each.getStage()).isEqualTo("STREAM_CANDIDATE");
            assertThat(each.getStatus()).isEqualTo("SUCCESS");
        });
    }

    @Test
    void shouldHideCandidateThatFailsBeforeFirstDeltaInChatstream() throws Exception {
        UserDO user = persistUser("USER");
        ChatSessionDO session = persistSession(user.getId(), ChatSessionStatusEnum.ACTIVE.getCode());
        when(chatModelGateway.classifyQuestionIntent("你好", List.of())).thenReturn("CHITCHAT");
        when(chatModelGateway.streamingCandidateIds()).thenReturn(List.of("qwen-plus", "glm-4.7"));
        when(chatModelGateway.tryAcquireStreamingCandidate("qwen-plus")).thenReturn(true);
        when(chatModelGateway.tryAcquireStreamingCandidate("glm-4.7")).thenReturn(true);
        when(chatModelGateway.streamChitchatByCandidate("qwen-plus", "你好", List.of()))
                .thenReturn(Flux.error(new RuntimeException("first token failed")));
        when(chatModelGateway.streamChitchatByCandidate("glm-4.7", "你好", List.of()))
                .thenReturn(Flux.just("你好，请问有什么可以帮你？"));
        when(chatModelGateway.candidateInfo("glm-4.7")).thenReturn(new ChatModelInfoRecord("siliconflow", "GLM-4.7"));

        MvcResult mvcResult = mockMvc.perform(MockMvcRequestBuilders.post("/chat-messages/chatstream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": %d,
                                  "userId": %d,
                                  "message": "你好",
                                  "traceId": "trace-first-token-failure"
                                }
                                """.formatted(session.getId(), user.getId())))
                .andExpect(request().asyncStarted())
                .andReturn();

        String responseBody = awaitResponseContains(mvcResult, "event:message_end");
        assertThat(responseBody).doesNotContain("event:reset");
        assertThat(responseBody).doesNotContain("qwen-plus");
        assertThat(countOccurrences(responseBody, "event:message_start")).isEqualTo(1);
        assertThat(responseBody).contains("glm-4.7");
        assertThat(responseBody).contains("event:message_end");
        assertThat(listMessages(session.getId())).hasSize(2);
    }

    private UserDO persistUser(String roleCode) {
        UserDO userDO = new UserDO();
        userDO.setId(ID_SEQUENCE.incrementAndGet());
        userDO.setUsername("chat-user-" + UUID.randomUUID());
        userDO.setDisplayName("chat-user");
        userDO.setRoleCode(roleCode);
        userDO.setDepartmentId(1L);
        userDO.setRankLevel(10);
        userDO.setStatus("ENABLED");
        userMapper.insert(userDO);
        userIds.add(userDO.getId());
        return userDO;
    }

    private ChatSessionDO persistSession(Long userId, String status) {
        ChatSessionDO sessionDO = new ChatSessionDO();
        sessionDO.setId(ID_SEQUENCE.incrementAndGet());
        sessionDO.setUserId(userId);
        sessionDO.setTitle("chat-session");
        sessionDO.setStatus(status);
        sessionDO.setLastActiveAt(new java.util.Date());
        chatSessionMapper.insert(sessionDO);
        sessionIds.add(sessionDO.getId());
        return sessionDO;
    }

    private long countMessages(Long sessionId) {
        return chatMessageMapper.selectCount(Wrappers.<ChatMessageDO>lambdaQuery()
                .eq(ChatMessageDO::getSessionId, sessionId)
                .eq(ChatMessageDO::getDeleteFlag, DeleteFlagEnum.NORMAL.getCode()));
    }

    private long countTraceLogs(Long sessionId) {
        return qaTraceLogMapper.selectCount(Wrappers.<QaTraceLogDO>lambdaQuery()
                .eq(QaTraceLogDO::getSessionId, sessionId)
                .eq(QaTraceLogDO::getDeleteFlag, DeleteFlagEnum.NORMAL.getCode()));
    }

    private List<ChatMessageDO> listMessages(Long sessionId) {
        return chatMessageMapper.selectList(Wrappers.<ChatMessageDO>lambdaQuery()
                .eq(ChatMessageDO::getSessionId, sessionId)
                .eq(ChatMessageDO::getDeleteFlag, DeleteFlagEnum.NORMAL.getCode())
                .orderByAsc(ChatMessageDO::getSequenceNo));
    }

    private List<QaTraceLogDO> listTraceLogs(Long sessionId) {
        return qaTraceLogMapper.selectList(Wrappers.<QaTraceLogDO>lambdaQuery()
                .eq(QaTraceLogDO::getSessionId, sessionId)
                .eq(QaTraceLogDO::getDeleteFlag, DeleteFlagEnum.NORMAL.getCode())
                .orderByAsc(QaTraceLogDO::getId));
    }

    private int countOccurrences(String text, String target) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(target, index)) >= 0) {
            count++;
            index += target.length();
        }
        return count;
    }

    private String awaitResponseContains(MvcResult mvcResult, String expected) throws Exception {
        long deadline = System.currentTimeMillis() + 3000L;
        String responseBody;
        do {
            responseBody = mvcResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
            if (responseBody.contains(expected)) {
                return responseBody;
            }
            Thread.sleep(50L);
        } while (System.currentTimeMillis() < deadline);
        return responseBody;
    }
}
