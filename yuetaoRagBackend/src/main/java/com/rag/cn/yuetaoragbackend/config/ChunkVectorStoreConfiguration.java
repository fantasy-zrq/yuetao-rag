package com.rag.cn.yuetaoragbackend.config;

import com.rag.cn.yuetaoragbackend.config.properties.AiProperties;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * @author zrq
 * 2026/04/27 10:00
 */
@Configuration
public class ChunkVectorStoreConfiguration {

    @Bean
    public PgVectorStore chunkVectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel, AiProperties aiProperties) {
        Integer dimension = aiProperties.getEmbedding().getCandidates().stream()
                .filter(each -> java.util.Objects.equals(each.getId(), aiProperties.getEmbedding().getDefaultModel()))
                .map(AiProperties.EmbeddingCandidateProperties::getDimension)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(1024);
        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
                .vectorTableName("t_chunk_vector")
                .idType(PgVectorStore.PgIdType.TEXT)
                .dimensions(dimension)
                .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
                .indexType(PgVectorStore.PgIndexType.NONE)
                .initializeSchema(false)
                .vectorTableValidationsEnabled(false)
                .build();
    }
}
