package com.rag.cn.yuetaoragbackend.config;

import com.rag.cn.yuetaoragbackend.config.properties.AiProperties;
import com.rag.cn.yuetaoragbackend.framework.exception.ServiceException;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * @author zrq
 * 2026/04/26 15:20
 */
@Configuration
public class AiModelConfiguration {

    @Bean
    public OpenAiCompatibleRerankModel rerankModel(AiProperties aiProperties) {
        AiProperties.RerankCandidateProperties candidate = aiProperties.getRerank().getCandidates().stream()
                .filter(each -> Objects.equals(each.getId(), aiProperties.getRerank().getDefaultModel()))
                .findFirst()
                .orElseThrow(() -> new ServiceException("未找到默认 Rerank 模型配置：" + aiProperties.getRerank().getDefaultModel()));
        AiProperties.ProviderProperties provider = aiProperties.getProviders().get(candidate.getProvider());
        if (provider == null) {
            throw new ServiceException("未找到 Rerank 提供商配置：" + candidate.getProvider());
        }
        return new OpenAiCompatibleRerankModel(
                provider.getUrl(),
                provider.getApiKey(),
                provider.getEndpoints().getRerank(),
                candidate.getModel());
    }

    @Bean
    public RoutingChatModel chatModel(AiProperties aiProperties) {
        List<AiProperties.ChatCandidateProperties> candidates = aiProperties.getChat().getCandidates().stream()
                .filter(each -> Boolean.TRUE.equals(each.getEnabled()))
                .sorted(Comparator.comparing(AiProperties.ChatCandidateProperties::getPriority, Comparator.nullsLast(Integer::compareTo)).reversed())
                .toList();
        if (candidates.isEmpty()) {
            throw new ServiceException("未配置启用的 Chat 模型候选");
        }
        List<ChatModel> models = new ArrayList<>();
        for (AiProperties.ChatCandidateProperties candidate : candidates) {
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
            models.add(OpenAiChatModel.builder()
                    .openAiApi(openAiApi)
                    .defaultOptions(options)
                    .observationRegistry(ObservationRegistry.NOOP)
                    .build());
        }
        return new RoutingChatModel(RoutingChatModel.runtimes(candidates, models), aiProperties.getCircuitBreaker());
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
