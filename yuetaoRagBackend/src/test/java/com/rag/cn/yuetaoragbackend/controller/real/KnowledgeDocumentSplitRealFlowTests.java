package com.rag.cn.yuetaoragbackend.controller.real;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.cn.yuetaoragbackend.dao.entity.KnowledgeBaseDO;
import com.rag.cn.yuetaoragbackend.dao.entity.KnowledgeDocumentDO;
import com.rag.cn.yuetaoragbackend.dao.mapper.KnowledgeBaseMapper;
import com.rag.cn.yuetaoragbackend.dao.mapper.KnowledgeDocumentMapper;
import com.rag.cn.yuetaoragbackend.framework.context.LoginUser;
import com.rag.cn.yuetaoragbackend.framework.context.UserContext;
import com.rag.cn.yuetaoragbackend.service.file.FileService;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * @author zrq
 * 2026/04/27 15:35
 */
@SpringBootTest
@AutoConfigureMockMvc
class KnowledgeDocumentSplitRealFlowTests {

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

    @Autowired
    private FileService fileService;

    private Long knowledgeBaseId;
    private Long documentId;
    private String bucketName;
    private String objectKey;

    @AfterEach
    void tearDown() {
        UserContext.clear();
        if (documentId != null) {
            jdbcTemplate.update("delete from t_chunk_vector where metadata->>'document_id' = ?", String.valueOf(documentId));
            jdbcTemplate.update("delete from t_chunk_department_auth where chunk_id in (select id from t_chunk where document_id = ?)", documentId);
            jdbcTemplate.update("delete from t_chunk where document_id = ?", documentId);
            jdbcTemplate.update("delete from t_document_department_auth where document_id = ?", documentId);
            KnowledgeDocumentDO documentDO = knowledgeDocumentMapper.selectById(documentId);
            if (documentDO != null && fileService.bucketExists(documentDO.getStorageBucket())) {
                try {
                    fileService.deleteObject(documentDO.getStorageBucket(), documentDO.getStorageKey());
                } catch (Exception ignored) {
                }
            }
            knowledgeDocumentMapper.deleteById(documentId);
        }
        if (knowledgeBaseId != null) {
            knowledgeBaseMapper.deleteById(knowledgeBaseId);
        }
        if (bucketName != null && fileService.bucketExists(bucketName)) {
            try {
                fileService.deleteBucket(bucketName);
            } catch (Exception ignored) {
            }
        }
    }

    @Test
    void shouldUploadAndSplitDocumentEndToEnd() throws Exception {
        UserContext.set(LoginUser.builder().userId("10001").build());
        String suffix = Long.toString(System.nanoTime(), 36).substring(0, 8);
        String knowledgeBaseName = "split-kb-" + suffix;
        bucketName = "sp" + suffix;

        MvcResult createKbResult = mockMvc.perform(MockMvcRequestBuilders.post("/knowledge-bases/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"%s",
                                  "description":"split flow verification",
                                  "embeddingModel":"text-embedding-v4",
                                  "collectionName":"%s"
                                }
                                """.formatted(knowledgeBaseName, bucketName)))
                .andReturn();
        JsonNode createKbJson = objectMapper.readTree(createKbResult.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(createKbJson.path("code").asText()).isEqualTo("0");
        knowledgeBaseId = createKbJson.path("data").path("id").asLong();

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "manual.txt",
                "text/plain",
                "abcdefghij".getBytes(StandardCharsets.UTF_8)
        );
        MvcResult uploadResult = mockMvc.perform(MockMvcRequestBuilders.multipart("/knowledge-documents/create")
                        .file(file)
                        .param("knowledgeBaseId", String.valueOf(knowledgeBaseId))
                        .param("chunkMode", "FIXED")
                        .param("chunkConfig", "{\"chunkSize\":4,\"chunkOverlap\":1}")
                        .param("visibilityScope", "INTERNAL")
                        .param("minRankLevel", "10"))
                .andReturn();
        JsonNode uploadJson = objectMapper.readTree(uploadResult.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(uploadJson.path("code").asText()).isEqualTo("0");
        documentId = uploadJson.path("data").path("id").asLong();

        KnowledgeDocumentDO documentDO = knowledgeDocumentMapper.selectById(documentId);
        assertThat(documentDO).isNotNull();
        objectKey = documentDO.getStorageKey();

        MvcResult splitResult = mockMvc.perform(MockMvcRequestBuilders.post("/knowledge-documents/split")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "documentId":%d
                                }
                                """.formatted(documentId)))
                .andReturn();
        JsonNode splitJson = objectMapper.readTree(splitResult.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(splitJson.path("code").asText()).isEqualTo("0");

        KnowledgeDocumentDO finished = waitUntilFinished(documentId, Duration.ofSeconds(90));
        assertThat(finished.getParseStatus()).isEqualTo("SUCCESS");

        Integer chunkCount = jdbcTemplate.queryForObject(
                "select count(*) from t_chunk where document_id = ? and delete_flag = 0",
                Integer.class,
                documentId
        );
        Integer vectorCount = jdbcTemplate.queryForObject(
                "select count(*) from t_chunk_vector where metadata->>'document_id' = ?",
                Integer.class,
                String.valueOf(documentId)
        );
        String firstMetadata = jdbcTemplate.queryForObject(
                "select metadata::text from t_chunk_vector where metadata->>'document_id' = ? order by id limit 1",
                String.class,
                String.valueOf(documentId)
        );

        assertThat(chunkCount).isEqualTo(3);
        assertThat(vectorCount).isEqualTo(3);
        assertThat(firstMetadata).contains("\"document_id\": \"" + documentId + "\"");
        assertThat(firstMetadata).contains("\"collection_name\": \"" + bucketName + "\"");

        System.out.println("REAL_SPLIT_KB_ID=" + knowledgeBaseId);
        System.out.println("REAL_SPLIT_DOCUMENT_ID=" + documentId);
        System.out.println("REAL_SPLIT_BUCKET=" + bucketName);
        System.out.println("REAL_SPLIT_OBJECT_KEY=" + objectKey);
        System.out.println("REAL_SPLIT_PARSE_STATUS=" + finished.getParseStatus());
        System.out.println("REAL_SPLIT_CHUNK_COUNT=" + chunkCount);
        System.out.println("REAL_SPLIT_VECTOR_COUNT=" + vectorCount);
        System.out.println("REAL_SPLIT_FIRST_METADATA=" + firstMetadata);
    }

    private KnowledgeDocumentDO waitUntilFinished(Long documentId, Duration timeout) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            KnowledgeDocumentDO documentDO = knowledgeDocumentMapper.selectById(documentId);
            if (documentDO != null && ("SUCCESS".equals(documentDO.getParseStatus()) || "FAILED".equals(documentDO.getParseStatus()))) {
                return documentDO;
            }
            Thread.sleep(2000L);
        }
        return knowledgeDocumentMapper.selectById(documentId);
    }
}
