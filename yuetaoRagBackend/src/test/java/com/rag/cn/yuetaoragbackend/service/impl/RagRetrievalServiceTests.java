package com.rag.cn.yuetaoragbackend.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rag.cn.yuetaoragbackend.config.OpenAiCompatibleRerankModel;
import com.rag.cn.yuetaoragbackend.config.properties.AiProperties;
import com.rag.cn.yuetaoragbackend.config.properties.AuthzProperties;
import com.rag.cn.yuetaoragbackend.config.properties.RagRetrievalProperties;
import com.rag.cn.yuetaoragbackend.dao.entity.UserDO;
import com.rag.cn.yuetaoragbackend.dao.mapper.ChunkVectorMapper;
import com.rag.cn.yuetaoragbackend.dao.projection.RetrievedChunk;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.EmbeddingModel;

/**
 * @author zrq
 * 2026/05/01 16:30
 */
@ExtendWith(MockitoExtension.class)
class RagRetrievalServiceTests {

    @Mock
    private ChunkVectorMapper chunkVectorMapper;

    @Mock
    private EmbeddingModel embeddingModel;

    @Mock
    private OpenAiCompatibleRerankModel rerankModel;

    @Test
    void shouldPassCorrectParametersToMapperForNormalUser() {
        RagRetrievalProperties retrievalProperties = new RagRetrievalProperties();
        retrievalProperties.setTopK(4);
        retrievalProperties.setCandidateMultiplier(2);
        AiProperties aiProperties = new AiProperties();
        AuthzProperties authzProperties = new AuthzProperties();
        authzProperties.setAdminRoleCodes(List.of("ADMIN"));
        RagRetrievalService retrievalService = new RagRetrievalService(
                chunkVectorMapper,
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
        when(chunkVectorMapper.selectByVectorSearch(
                anyString(), anyBoolean(), anyInt(), anyLong(), anyBoolean(), anyString(), anyInt()))
                .thenReturn(List.of());

        retrievalService.retrieve(userDO, "报销制度");

        ArgumentCaptor<String> vectorCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Boolean> adminCaptor = ArgumentCaptor.forClass(Boolean.class);
        ArgumentCaptor<Integer> rankCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Long> deptCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Boolean> hasCollCaptor = ArgumentCaptor.forClass(Boolean.class);
        ArgumentCaptor<String> collCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Integer> limitCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(chunkVectorMapper).selectByVectorSearch(
                vectorCaptor.capture(), adminCaptor.capture(), rankCaptor.capture(),
                deptCaptor.capture(), hasCollCaptor.capture(), collCaptor.capture(), limitCaptor.capture());
        assertThat(adminCaptor.getValue()).isFalse();
        assertThat(rankCaptor.getValue()).isEqualTo(10);
        assertThat(deptCaptor.getValue()).isEqualTo(12L);
        assertThat(hasCollCaptor.getValue()).isFalse();
        assertThat(limitCaptor.getValue()).isEqualTo(8); // topK=4 * multiplier=2
    }

    @Test
    void shouldPassAdminFlagForAdminUser() {
        RagRetrievalProperties retrievalProperties = new RagRetrievalProperties();
        retrievalProperties.setTopK(4);
        retrievalProperties.setCandidateMultiplier(2);
        AiProperties aiProperties = new AiProperties();
        AuthzProperties authzProperties = new AuthzProperties();
        authzProperties.setAdminRoleCodes(List.of("ADMIN"));
        RagRetrievalService retrievalService = new RagRetrievalService(
                chunkVectorMapper,
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
        when(chunkVectorMapper.selectByVectorSearch(
                anyString(), anyBoolean(), anyInt(), anyLong(), anyBoolean(), anyString(), anyInt()))
                .thenReturn(List.of());

        retrievalService.retrieve(userDO, "报销制度");

        ArgumentCaptor<Boolean> adminCaptor = ArgumentCaptor.forClass(Boolean.class);
        verify(chunkVectorMapper).selectByVectorSearch(
                anyString(), adminCaptor.capture(), anyInt(), anyLong(),
                anyBoolean(), anyString(), anyInt());
        assertThat(adminCaptor.getValue()).isTrue();
    }

    @Test
    void shouldPassCollectionNameForScopedRetrieval() {
        RagRetrievalProperties retrievalProperties = new RagRetrievalProperties();
        retrievalProperties.setTopK(4);
        retrievalProperties.setCandidateMultiplier(2);
        AiProperties aiProperties = new AiProperties();
        AuthzProperties authzProperties = new AuthzProperties();
        authzProperties.setAdminRoleCodes(List.of("ADMIN"));
        RagRetrievalService retrievalService = new RagRetrievalService(
                chunkVectorMapper,
                embeddingModel,
                retrievalProperties,
                aiProperties,
                authzProperties,
                rerankModel);
        UserDO userDO = new UserDO()
                .setRoleCode("ADMIN")
                .setDepartmentId(99L)
                .setRankLevel(1);
        when(embeddingModel.embed("退货政策")).thenReturn(new float[]{0.3F, 0.4F});
        when(chunkVectorMapper.selectByVectorSearch(
                anyString(), anyBoolean(), anyInt(), anyLong(), anyBoolean(), anyString(), anyInt()))
                .thenReturn(List.of());

        retrievalService.retrieveByCollection(userDO, "退货政策", "return_policy_kb");

        ArgumentCaptor<Boolean> hasCollCaptor = ArgumentCaptor.forClass(Boolean.class);
        ArgumentCaptor<String> collCaptor = ArgumentCaptor.forClass(String.class);
        verify(chunkVectorMapper).selectByVectorSearch(
                anyString(), anyBoolean(), anyInt(), anyLong(),
                hasCollCaptor.capture(), collCaptor.capture(), anyInt());
        assertThat(hasCollCaptor.getValue()).isTrue();
        assertThat(collCaptor.getValue()).isEqualTo("return_policy_kb");
    }
}
