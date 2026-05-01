package com.rag.cn.yuetaoragbackend.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rag.cn.yuetaoragbackend.config.OpenAiCompatibleRerankModel;
import com.rag.cn.yuetaoragbackend.config.properties.AiProperties;
import com.rag.cn.yuetaoragbackend.config.properties.AuthzProperties;
import com.rag.cn.yuetaoragbackend.config.properties.RagRetrievalProperties;
import com.rag.cn.yuetaoragbackend.dao.entity.UserDO;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/**
 * @author zrq
 * 2026/05/01 16:30
 */
@ExtendWith(MockitoExtension.class)
class RagRetrievalServiceTests {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private EmbeddingModel embeddingModel;

    @Mock
    private OpenAiCompatibleRerankModel rerankModel;

    @Test
    void shouldFilterSensitiveDocumentsByUserDepartment() {
        RagRetrievalProperties retrievalProperties = new RagRetrievalProperties();
        retrievalProperties.setTopK(4);
        retrievalProperties.setCandidateMultiplier(2);
        AiProperties aiProperties = new AiProperties();
        AuthzProperties authzProperties = new AuthzProperties();
        authzProperties.setAdminRoleCodes(List.of("ADMIN"));
        RagRetrievalService retrievalService = new RagRetrievalService(
                jdbcTemplate,
                embeddingModel,
                retrievalProperties,
                aiProperties,
                authzProperties,
                rerankModel);
        UserDO userDO = new UserDO()
                .setRoleCode("USER")
                .setDepartmentId(12L)
                .setRankLevel(10);
        when(embeddingModel.embed("报销制度")).thenReturn(new float[]{0.1F, 0.2F});
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

        retrievalService.retrieve(userDO, "报销制度");

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> paramsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), any(RowMapper.class), paramsCaptor.capture());
        assertThat(sqlCaptor.getValue()).contains("t_document_department_auth");
        assertThat(sqlCaptor.getValue()).contains("kd.visibility_scope <> 'SENSITIVE'");
        assertThat(sqlCaptor.getValue()).contains("dda.department_id");
        assertThat(paramsCaptor.getValue()).contains(12L);
    }

    @Test
    void shouldBypassRankAndDepartmentAuthForAdmin() {
        RagRetrievalProperties retrievalProperties = new RagRetrievalProperties();
        retrievalProperties.setTopK(4);
        retrievalProperties.setCandidateMultiplier(2);
        AiProperties aiProperties = new AiProperties();
        AuthzProperties authzProperties = new AuthzProperties();
        authzProperties.setAdminRoleCodes(List.of("ADMIN"));
        RagRetrievalService retrievalService = new RagRetrievalService(
                jdbcTemplate,
                embeddingModel,
                retrievalProperties,
                aiProperties,
                authzProperties,
                rerankModel);
        UserDO userDO = new UserDO()
                .setRoleCode("ADMIN")
                .setDepartmentId(99L)
                .setRankLevel(1);
        when(embeddingModel.embed("报销制度")).thenReturn(new float[]{0.1F, 0.2F});
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

        retrievalService.retrieve(userDO, "报销制度");

        ArgumentCaptor<Object[]> paramsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).query(anyString(), any(RowMapper.class), paramsCaptor.capture());
        assertThat(paramsCaptor.getValue()).contains(true, true);
    }
}
