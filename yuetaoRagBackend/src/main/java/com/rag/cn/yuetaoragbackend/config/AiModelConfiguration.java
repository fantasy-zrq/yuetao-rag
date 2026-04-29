package com.rag.cn.yuetaoragbackend.config;

import com.rag.cn.yuetaoragbackend.config.properties.AiProperties;
import com.rag.cn.yuetaoragbackend.framework.exception.ServiceException;

import java.util.Objects;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
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
    public ChatModel chatModel(AiProperties aiProperties) {
        AiProperties.ChatCandidateProperties candidate = aiProperties.getChat().getCandidates().stream()
                .filter(each -> Boolean.TRUE.equals(each.getEnabled()))
                .filter(each -> Objects.equals(each.getId(), aiProperties.getChat().getDefaultModel()))
                .findFirst()
                .orElseThrow(() -> new ServiceException("未找到默认 Chat 模型配置：" + aiProperties.getChat().getDefaultModel()));
        AiProperties.ProviderProperties provider = aiProperties.getProviders().get(candidate.getProvider());
        if (provider == null) {
            throw new ServiceException("未找到 Chat 提供商配置：" + candidate.getProvider());
        }
        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(provider.getUrl())
                .apiKey(provider.getApiKey())
                .completionsPath(provider.getEndpoints().getChat())
                .build();
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(candidate.getModel())
                .temperature(0.2)
                .build();
        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(options)
                .observationRegistry(ObservationRegistry.NOOP)
                .build();
    }

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
