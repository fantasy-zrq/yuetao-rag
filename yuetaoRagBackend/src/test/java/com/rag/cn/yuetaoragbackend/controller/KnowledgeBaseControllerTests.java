package com.rag.cn.yuetaoragbackend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.cn.yuetaoragbackend.config.enums.CommonStatusEnum;
import com.rag.cn.yuetaoragbackend.config.enums.DeleteFlagEnum;
import com.rag.cn.yuetaoragbackend.dao.entity.KnowledgeBaseDO;
import com.rag.cn.yuetaoragbackend.dao.mapper.KnowledgeBaseMapper;
import com.rag.cn.yuetaoragbackend.dao.mapper.KnowledgeDocumentMapper;
import com.rag.cn.yuetaoragbackend.framework.context.LoginUser;
import com.rag.cn.yuetaoragbackend.framework.context.UserContext;
import com.rag.cn.yuetaoragbackend.service.file.FileService;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * @author zrq
 * 2026/04/26 15:35
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
class KnowledgeBaseControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private KnowledgeBaseMapper knowledgeBaseMapper;

    @Autowired
    private KnowledgeDocumentMapper knowledgeDocumentMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private FileService fileService;

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
    void shouldCreateKnowledgeBaseThroughController() throws Exception {
        UserContext.set(LoginUser.builder().userId("10001").build());
        String suffix = uniqueSuffix();
        String name = "it-kb-" + suffix;
        String collectionName = "kb" + suffix;

        MvcResult mvcResult = mockMvc.perform(MockMvcRequestBuilders.post("/knowledge-bases/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"%s",
                                  "description":"controller-create",
                                  "embeddingModel":"text-embedding-v4",
                                  "collectionName":"%s"
                                }
                                """.formatted(name, collectionName)))
                .andReturn();

        String body = mvcResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(mvcResult.getResponse().getStatus()).isEqualTo(200);
        assertThat(body).contains("\"code\":\"0\"");
        Long knowledgeBaseId = objectMapper.readTree(body).path("data").path("id").asLong();
        knowledgeBaseIds.add(knowledgeBaseId);
        KnowledgeBaseDO knowledgeBaseDO = knowledgeBaseMapper.selectById(knowledgeBaseId);
        assertThat(knowledgeBaseDO).isNotNull();
        assertThat(knowledgeBaseDO.getName()).isEqualTo(name);
        assertThat(knowledgeBaseDO.getCollectionName()).isEqualTo(collectionName);
        assertThat(knowledgeBaseDO.getStatus()).isEqualTo(CommonStatusEnum.ENABLED.getCode());
        assertThat(knowledgeBaseDO.getCreatedBy()).isEqualTo(10001L);
        verify(fileService).createBucketIfAbsent(collectionName);
    }

    @Test
    void shouldUpdateKnowledgeBaseNameThroughController() throws Exception {
        UserContext.set(LoginUser.builder().userId("10001").build());
        KnowledgeBaseDO knowledgeBaseDO = persistKnowledgeBase("old-name-" + uniqueSuffix(), "kb" + uniqueSuffix());

        MvcResult mvcResult = mockMvc.perform(MockMvcRequestBuilders.post("/knowledge-bases/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "id":%d,
                                  "name":"updated-name"
                                }
                                """.formatted(knowledgeBaseDO.getId())))
                .andReturn();

        String body = mvcResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(mvcResult.getResponse().getStatus()).isEqualTo(200);
        assertThat(body).contains("\"code\":\"0\"");
        KnowledgeBaseDO updated = knowledgeBaseMapper.selectById(knowledgeBaseDO.getId());
        assertThat(updated.getName()).isEqualTo("updated-name");
        assertThat(updated.getCollectionName()).isEqualTo(knowledgeBaseDO.getCollectionName());
        assertThat(updated.getUpdatedBy()).isEqualTo(10001L);
    }

    @Test
    void shouldDeleteKnowledgeBaseThroughControllerWhenNoDocumentsExist() throws Exception {
        UserContext.set(LoginUser.builder().userId("10001").build());
        String suffix = uniqueSuffix();
        KnowledgeBaseDO knowledgeBaseDO = persistKnowledgeBase("delete-name-" + suffix, "kb" + suffix);

        MvcResult mvcResult = mockMvc.perform(MockMvcRequestBuilders.post("/knowledge-bases/delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "id":%d
                                }
                                """.formatted(knowledgeBaseDO.getId())))
                .andReturn();

        String body = mvcResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(mvcResult.getResponse().getStatus()).isEqualTo(200);
        assertThat(body).contains("\"code\":\"0\"");
        KnowledgeBaseDO deleted = knowledgeBaseMapper.selectById(knowledgeBaseDO.getId());
        assertThat(deleted.getDeleteFlag()).isEqualTo(DeleteFlagEnum.DELETED.getCode());
        verify(fileService).deleteBucket("kb" + suffix);
    }

    @Test
    void shouldRejectDeleteWhenKnowledgeBaseStillHasDocuments() throws Exception {
        UserContext.set(LoginUser.builder().userId("10001").build());
        String suffix = uniqueSuffix();
        KnowledgeBaseDO knowledgeBaseDO = persistKnowledgeBase("blocked-name-" + suffix, "kb" + suffix);
        persistKnowledgeDocument(knowledgeBaseDO.getId(), "doc-" + suffix, "kb" + suffix);

        MvcResult mvcResult = mockMvc.perform(MockMvcRequestBuilders.post("/knowledge-bases/delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "id":%d
                                }
                                """.formatted(knowledgeBaseDO.getId())))
                .andReturn();

        String body = mvcResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(mvcResult.getResponse().getStatus()).isEqualTo(200);
        assertThat(body).contains("知识库仍有文档");
        KnowledgeBaseDO existing = knowledgeBaseMapper.selectById(knowledgeBaseDO.getId());
        assertThat(existing.getDeleteFlag()).isEqualTo(DeleteFlagEnum.NORMAL.getCode());
        verify(fileService, never()).deleteBucket("kb" + suffix);
    }

    private KnowledgeBaseDO persistKnowledgeBase(String name, String collectionName) {
        KnowledgeBaseDO knowledgeBaseDO = new KnowledgeBaseDO()
                .setName(name)
                .setDescription("test")
                .setStatus(CommonStatusEnum.ENABLED.getCode())
                .setEmbeddingModel("text-embedding-v4")
                .setCollectionName(collectionName)
                .setCreatedBy(10001L)
                .setUpdatedBy(10001L);
        knowledgeBaseMapper.insert(knowledgeBaseDO);
        knowledgeBaseIds.add(knowledgeBaseDO.getId());
        return knowledgeBaseDO;
    }

    private void persistKnowledgeDocument(Long knowledgeBaseId, String title, String bucketName) {
        long documentId = Math.abs(System.nanoTime());
        jdbcTemplate.update("""
                        INSERT INTO t_knowledge_document
                        (id, knowledge_base_id, title, source_type, mime_type, storage_bucket, storage_key, storage_url,
                         file_size, parse_status, visibility_scope, min_rank_level, status, created_by, updated_by,
                         delete_flag, chunk_mode, chunk_config)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS json))
                        """,
                documentId, knowledgeBaseId, title, "UPLOAD", "text/plain", bucketName, title + ".txt",
                "http://test/" + title + ".txt", 1L, "PENDING", "INTERNAL", 10, CommonStatusEnum.ENABLED.getCode(),
                10001L, 10001L, DeleteFlagEnum.NORMAL.getCode(), "FIXED", "{\"chunkSize\":512,\"chunkOverlap\":50}");
        knowledgeDocumentIds.add(documentId);
    }

    private String uniqueSuffix() {
        String suffix = Long.toString(System.nanoTime(), 36);
        return suffix.substring(Math.max(0, suffix.length() - 8));
    }
}
