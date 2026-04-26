package com.rag.cn.yuetaoragbackend.controller.real;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.cn.yuetaoragbackend.dao.entity.KnowledgeBaseDO;
import com.rag.cn.yuetaoragbackend.dao.mapper.KnowledgeBaseMapper;
import com.rag.cn.yuetaoragbackend.framework.context.LoginUser;
import com.rag.cn.yuetaoragbackend.framework.context.UserContext;
import com.rag.cn.yuetaoragbackend.service.file.FileService;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * @author zrq
 * 2026/04/26 16:05
 */
@SpringBootTest
@AutoConfigureMockMvc
class KnowledgeBaseControllerRealCreateTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private KnowledgeBaseMapper knowledgeBaseMapper;

    @Autowired
    private FileService fileService;

    private Long createdKnowledgeBaseId;
    private String createdBucketName;

    @AfterEach
    void tearDown() {
        UserContext.clear();
        if (createdKnowledgeBaseId != null) {
            knowledgeBaseMapper.deleteById(createdKnowledgeBaseId);
        }
        if (createdBucketName != null && fileService.bucketExists(createdBucketName)) {
            fileService.deleteBucket(createdBucketName);
        }
    }

    @Test
    void shouldCreateKnowledgeBaseAndPersistToDatabaseAndRustfs() throws Exception {
        UserContext.set(LoginUser.builder().userId("10001").build());
        String suffix = Long.toString(System.nanoTime(), 36).substring(0, 8);
        String knowledgeBaseName = "real-kb-" + suffix;
        String bucketName = "rkb" + suffix;

        MvcResult mvcResult = mockMvc.perform(MockMvcRequestBuilders.post("/knowledge-bases/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"%s",
                                  "description":"real create verification",
                                  "embeddingModel":"text-embedding-v4",
                                  "collectionName":"%s"
                                }
                                """.formatted(knowledgeBaseName, bucketName)))
                .andReturn();

        String responseBody = mvcResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode responseJson = objectMapper.readTree(responseBody);
        assertThat(mvcResult.getResponse().getStatus()).isEqualTo(200);
        assertThat(responseJson.path("code").asText()).isEqualTo("0");

        createdKnowledgeBaseId = responseJson.path("data").path("id").asLong();
        createdBucketName = bucketName;

        KnowledgeBaseDO knowledgeBaseDO = knowledgeBaseMapper.selectById(createdKnowledgeBaseId);
        boolean bucketExists = fileService.bucketExists(bucketName);

        assertThat(knowledgeBaseDO).isNotNull();
        assertThat(knowledgeBaseDO.getName()).isEqualTo(knowledgeBaseName);
        assertThat(knowledgeBaseDO.getCollectionName()).isEqualTo(bucketName);
        assertThat(bucketExists).isTrue();

        System.out.println("REAL_CREATE_KB_ID=" + createdKnowledgeBaseId);
        System.out.println("REAL_CREATE_KB_NAME=" + knowledgeBaseDO.getName());
        System.out.println("REAL_CREATE_BUCKET=" + knowledgeBaseDO.getCollectionName());
        System.out.println("REAL_CREATE_DB_DELETE_FLAG=" + knowledgeBaseDO.getDeleteFlag());
        System.out.println("REAL_CREATE_BUCKET_EXISTS=" + bucketExists);
    }
}
