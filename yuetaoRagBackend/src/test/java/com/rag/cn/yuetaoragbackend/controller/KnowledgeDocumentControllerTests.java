package com.rag.cn.yuetaoragbackend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.cn.yuetaoragbackend.config.enums.DeleteFlagEnum;
import com.rag.cn.yuetaoragbackend.config.enums.ParseStatusEnum;
import com.rag.cn.yuetaoragbackend.dao.entity.KnowledgeBaseDO;
import com.rag.cn.yuetaoragbackend.dao.entity.KnowledgeDocumentDO;
import com.rag.cn.yuetaoragbackend.dao.mapper.KnowledgeBaseMapper;
import com.rag.cn.yuetaoragbackend.dao.mapper.KnowledgeDocumentMapper;
import com.rag.cn.yuetaoragbackend.framework.context.LoginUser;
import com.rag.cn.yuetaoragbackend.framework.context.UserContext;
import com.rag.cn.yuetaoragbackend.mq.producer.MessageQueueProducer;
import com.rag.cn.yuetaoragbackend.service.file.FileService;
import com.rag.cn.yuetaoragbackend.service.file.UploadObjectResult;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * @author zrq
 * 2026/04/26 16:35
 */
@SpringBootTest(properties = {
        "app.ai.providers.bailian.api-key=test-bailian-key",
        "app.ai.embedding.default-model=text-embedding-v4",
        "app.ai.embedding.candidates[0].id=text-embedding-v4",
        "app.ai.embedding.candidates[0].provider=bailian",
        "app.ai.embedding.candidates[0].model=text-embedding-v4",
        "app.ai.embedding.candidates[0].dimension=1024",
        "spring.ai.vectorstore.pgvector.initialize-schema=false"
})
@AutoConfigureMockMvc
class KnowledgeDocumentControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private KnowledgeBaseMapper knowledgeBaseMapper;

    @Autowired
    private KnowledgeDocumentMapper knowledgeDocumentMapper;

    @MockBean
    private FileService fileService;

    @MockBean
    private MessageQueueProducer messageQueueProducer;

    private final List<Long> knowledgeBaseIds = new ArrayList<>();
    private final List<Long> knowledgeDocumentIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        UserContext.clear();
        knowledgeDocumentIds.forEach(knowledgeDocumentMapper::deleteById);
        knowledgeBaseIds.forEach(knowledgeBaseMapper::deleteById);
        knowledgeDocumentIds.clear();
        knowledgeBaseIds.clear();
    }

    @Test
    void shouldCreateKnowledgeDocumentThroughController() throws Exception {
        UserContext.set(LoginUser.builder().userId("10001").build());
        KnowledgeBaseDO knowledgeBaseDO = persistKnowledgeBase("doc-upload-kb", "doc-upload-bucket");
        MockMultipartFile file = new MockMultipartFile("file", "manual.pdf", "application/pdf", "hello".getBytes());
        when(fileService.uploadObject(eq("doc-upload-bucket"), any(String.class), eq(file.getBytes()), eq("application/pdf")))
                .thenReturn(new UploadObjectResult("etag-doc-001", "http://rustfs/doc-upload-bucket/manual.pdf"));

        MvcResult mvcResult = mockMvc.perform(MockMvcRequestBuilders.multipart("/knowledge-documents/create")
                        .file(file)
                        .param("knowledgeBaseId", knowledgeBaseDO.getId().toString())
                        .param("chunkMode", "FIXED")
                        .param("chunkConfig", "{\"chunkSize\":512,\"chunkOverlap\":50}")
                        .param("visibilityScope", "INTERNAL")
                        .param("minRankLevel", "10"))
                .andReturn();

        String body = mvcResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(mvcResult.getResponse().getStatus()).isEqualTo(200);
        JsonNode responseJson = objectMapper.readTree(body);
        assertThat(responseJson.path("code").asText()).isEqualTo("0");
        Long documentId = responseJson.path("data").path("id").asLong();
        knowledgeDocumentIds.add(documentId);

        KnowledgeDocumentDO knowledgeDocumentDO = knowledgeDocumentMapper.selectById(documentId);
        assertThat(knowledgeDocumentDO).isNotNull();
        assertThat(knowledgeDocumentDO.getKnowledgeBaseId()).isEqualTo(knowledgeBaseDO.getId());
        assertThat(knowledgeDocumentDO.getTitle()).isEqualTo("manual.pdf");
        assertThat(knowledgeDocumentDO.getStorageBucket()).isEqualTo("doc-upload-bucket");
        assertThat(knowledgeDocumentDO.getStorageKey()).endsWith("_manual.pdf");
        assertThat(knowledgeDocumentDO.getStorageEtag()).isEqualTo("etag-doc-001");
        assertThat(knowledgeDocumentDO.getStorageUrl()).isEqualTo("http://rustfs/doc-upload-bucket/manual.pdf");
        assertThat(knowledgeDocumentDO.getChunkMode()).isEqualTo("FIXED");
        assertThat(knowledgeDocumentDO.getChunkConfig()).isEqualTo("{\"chunkSize\":512,\"chunkOverlap\":50}");
        verify(fileService).uploadObject(eq("doc-upload-bucket"), any(String.class), eq(file.getBytes()), eq("application/pdf"));
    }

    @Test
    void shouldUpdateKnowledgeDocumentTitleOnlyThroughController() throws Exception {
        UserContext.set(LoginUser.builder().userId("10001").build());
        KnowledgeBaseDO knowledgeBaseDO = persistKnowledgeBase("doc-update-kb", "doc-update-bucket");
        KnowledgeDocumentDO knowledgeDocumentDO = persistKnowledgeDocument(knowledgeBaseDO.getId(), ParseStatusEnum.SUCCESS.getCode());

        MvcResult mvcResult = mockMvc.perform(MockMvcRequestBuilders.post("/knowledge-documents/update")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "id":%d,
                                  "title":"renamed.pdf",
                                  "chunkMode":"FIXED",
                                  "chunkConfig":"{\\"chunkSize\\":512,\\"chunkOverlap\\":50}",
                                  "visibilityScope":"INTERNAL",
                                  "minRankLevel":10
                                }
                                """.formatted(knowledgeDocumentDO.getId())))
                .andReturn();

        JsonNode responseJson = objectMapper.readTree(mvcResult.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(responseJson.path("code").asText()).isEqualTo("0");
        KnowledgeDocumentDO updated = knowledgeDocumentMapper.selectById(knowledgeDocumentDO.getId());
        assertThat(updated.getTitle()).isEqualTo("renamed.pdf");
        assertThat(updated.getParseStatus()).isEqualTo(ParseStatusEnum.SUCCESS.getCode());
    }

    @Test
    void shouldResetToProcessingWhenChunkConfigChangesThroughController() throws Exception {
        UserContext.set(LoginUser.builder().userId("10001").build());
        KnowledgeBaseDO knowledgeBaseDO = persistKnowledgeBase("doc-rebuild-kb", "doc-rebuild-bucket");
        KnowledgeDocumentDO knowledgeDocumentDO = persistKnowledgeDocument(knowledgeBaseDO.getId(), ParseStatusEnum.SUCCESS.getCode());
        doAnswer(invocation -> {
            ((java.util.function.Consumer<Object>) invocation.getArgument(4)).accept(null);
            return null;
        }).when(messageQueueProducer).sendInTransaction(any(), any(), any(), any(), any());

        MvcResult mvcResult = mockMvc.perform(MockMvcRequestBuilders.post("/knowledge-documents/update")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "id":%d,
                                  "title":"manual.pdf",
                                  "chunkMode":"STRUCTURE_AWARE",
                                  "chunkConfig":"{\\"chunkSize\\":800,\\"chunkOverlap\\":80}",
                                  "visibilityScope":"INTERNAL",
                                  "minRankLevel":10
                                }
                                """.formatted(knowledgeDocumentDO.getId())))
                .andReturn();

        JsonNode responseJson = objectMapper.readTree(mvcResult.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(responseJson.path("code").asText()).isEqualTo("0");
        KnowledgeDocumentDO updated = knowledgeDocumentMapper.selectById(knowledgeDocumentDO.getId());
        assertThat(updated.getChunkMode()).isEqualTo("STRUCTURE_AWARE");
        assertThat(updated.getParseStatus()).isEqualTo(ParseStatusEnum.PROCESSING.getCode());
    }

    @Test
    void shouldTriggerSplitThroughController() throws Exception {
        UserContext.set(LoginUser.builder().userId("10001").build());
        KnowledgeBaseDO knowledgeBaseDO = persistKnowledgeBase("doc-split-kb", "doc-split-bucket");
        KnowledgeDocumentDO knowledgeDocumentDO = persistKnowledgeDocument(knowledgeBaseDO.getId(), ParseStatusEnum.PENDING.getCode());
        doAnswer(invocation -> {
            ((java.util.function.Consumer<Object>) invocation.getArgument(4)).accept(null);
            return null;
        }).when(messageQueueProducer).sendInTransaction(any(), any(), any(), any(), any());

        MvcResult mvcResult = mockMvc.perform(MockMvcRequestBuilders.post("/knowledge-documents/split")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "documentId":%d
                                }
                                """.formatted(knowledgeDocumentDO.getId())))
                .andReturn();

        JsonNode responseJson = objectMapper.readTree(mvcResult.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(responseJson.path("code").asText()).isEqualTo("0");
        KnowledgeDocumentDO updated = knowledgeDocumentMapper.selectById(knowledgeDocumentDO.getId());
        assertThat(updated.getParseStatus()).isEqualTo(ParseStatusEnum.PROCESSING.getCode());
        verify(messageQueueProducer).sendInTransaction(any(), any(), any(), any(), any());
    }

    @Test
    void shouldRejectDeleteWhenDocumentIsProcessingThroughController() throws Exception {
        UserContext.set(LoginUser.builder().userId("10001").build());
        KnowledgeBaseDO knowledgeBaseDO = persistKnowledgeBase("doc-delete-kb", "doc-delete-bucket");
        KnowledgeDocumentDO knowledgeDocumentDO = persistKnowledgeDocument(knowledgeBaseDO.getId(), ParseStatusEnum.PROCESSING.getCode());

        MvcResult mvcResult = mockMvc.perform(MockMvcRequestBuilders.post("/knowledge-documents/delete")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "id":%d
                                }
                                """.formatted(knowledgeDocumentDO.getId())))
                .andReturn();

        String body = mvcResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).contains("文档处理中");
        verify(fileService, never()).deleteObject(any(String.class), any(String.class));
    }

    @Test
    void shouldDeleteKnowledgeDocumentThroughController() throws Exception {
        UserContext.set(LoginUser.builder().userId("10001").build());
        KnowledgeBaseDO knowledgeBaseDO = persistKnowledgeBase("doc-delete-success-kb", "doc-delete-success-bucket");
        KnowledgeDocumentDO knowledgeDocumentDO = persistKnowledgeDocument(knowledgeBaseDO.getId(), ParseStatusEnum.SUCCESS.getCode());

        MvcResult mvcResult = mockMvc.perform(MockMvcRequestBuilders.post("/knowledge-documents/delete")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "id":%d
                                }
                                """.formatted(knowledgeDocumentDO.getId())))
                .andReturn();

        JsonNode responseJson = objectMapper.readTree(mvcResult.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(responseJson.path("code").asText()).isEqualTo("0");
        KnowledgeDocumentDO deleted = knowledgeDocumentMapper.selectById(knowledgeDocumentDO.getId());
        assertThat(deleted.getDeleteFlag()).isEqualTo(DeleteFlagEnum.DELETED.getCode());
        verify(fileService).deleteObject("doc-update-bucket", "manual.pdf");
    }

    private KnowledgeBaseDO persistKnowledgeBase(String name, String collectionName) {
        KnowledgeBaseDO knowledgeBaseDO = new KnowledgeBaseDO()
                .setName(name)
                .setDescription("test")
                .setStatus("ENABLED")
                .setEmbeddingModel("text-embedding-v4")
                .setCollectionName(collectionName)
                .setCreatedBy(10001L)
                .setUpdatedBy(10001L);
        knowledgeBaseMapper.insert(knowledgeBaseDO);
        knowledgeBaseIds.add(knowledgeBaseDO.getId());
        return knowledgeBaseDO;
    }

    private KnowledgeDocumentDO persistKnowledgeDocument(Long knowledgeBaseId, String parseStatus) {
        KnowledgeDocumentDO knowledgeDocumentDO = new KnowledgeDocumentDO()
                .setKnowledgeBaseId(knowledgeBaseId)
                .setTitle("manual.pdf")
                .setSourceType("UPLOAD")
                .setMimeType("application/pdf")
                .setStorageBucket("doc-update-bucket")
                .setStorageKey("manual.pdf")
                .setStorageEtag("etag-doc")
                .setStorageUrl("http://rustfs/doc-update-bucket/manual.pdf")
                .setFileSize(5L)
                .setParseStatus(parseStatus)
                .setChunkMode("FIXED")
                .setChunkConfig("{\"chunkSize\":512,\"chunkOverlap\":50}")
                .setVisibilityScope("INTERNAL")
                .setMinRankLevel(10)
                .setStatus("ENABLED")
                .setCreatedBy(10001L)
                .setUpdatedBy(10001L);
        knowledgeDocumentMapper.insert(knowledgeDocumentDO);
        knowledgeDocumentIds.add(knowledgeDocumentDO.getId());
        return knowledgeDocumentDO;
    }
}
