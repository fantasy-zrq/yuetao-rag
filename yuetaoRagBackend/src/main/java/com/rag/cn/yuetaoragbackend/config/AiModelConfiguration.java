package com.rag.cn.yuetaoragbackend.config;

import com.rag.cn.yuetaoragbackend.config.properties.AiProperties;
import com.rag.cn.yuetaoragbackend.framework.exception.ServiceException;

import java.util.Objects;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author zrq
 * 2026/04/26 15:20
 */
@Configuration
public class AiModelConfiguration {

    @Bean
    public EmbeddingModel embeddingModel(AiProperties aiProperties) {
        AiProperties.EmbeddingCandidateProperties candidate = aiProperties.getEmbedding().getCandidates().stream()
                .filter(each -> Objects.equals(each.getId(), aiProperties.getEmbedding().getDefaultModel()))
                .findFirst()
                .orElseThrow(() -> new ServiceException("未找到默认 Embedding 模型配置：" + aiProperties.getEmbedding().getDefaultModel()));
        AiProperties.ProviderProperties provider = aiProperties.getProviders().get(candidate.getProvider());
        if (provider == null) {
            throw new ServiceException("未找到 Embedding 提供商配置：" + candidate.getProvider());
        }
        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(provider.getUrl())
                .apiKey(provider.getApiKey())
                .embeddingsPath(provider.getEndpoints().getEmbedding())
                .build();
        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .model(candidate.getModel())
                .dimensions(candidate.getDimension())
                .build();
        return new OpenAiEmbeddingModel(openAiApi, org.springframework.ai.document.MetadataMode.EMBED, options) {
            @Override
            public int dimensions() {
                if (candidate.getDimension() != null && candidate.getDimension() > 0) {
                    return candidate.getDimension();
                }
                return super.dimensions();
            }
        };
    }
}
