package com.rag.cn.yuetaoragbackend.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.rag.cn.yuetaoragbackend.config.properties.AiProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * @author zrq
 * 2026/04/26 15:20
 */
class AiModelConfigurationTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(TestConfiguration.class, AiModelConfiguration.class)
            .withPropertyValues(
                    "app.ai.providers.bailian.url=https://dashscope.aliyuncs.com",
                    "app.ai.providers.bailian.api-key=test-bailian-key",
                    "app.ai.providers.bailian.endpoints.chat=/compatible-mode/v1/chat/completions",
                    "app.ai.providers.bailian.endpoints.embedding=/compatible-mode/v1/embeddings",
                    "app.ai.providers.bailian.endpoints.rerank=/api/v1/services/rerank/text-rerank/text-rerank",
                    "app.ai.chat.default-model=qwen-plus",
                    "app.ai.chat.candidates[0].id=qwen-plus",
                    "app.ai.chat.candidates[0].provider=bailian",
                    "app.ai.chat.candidates[0].enabled=true",
                    "app.ai.chat.candidates[0].model=qwen-plus",
                    "app.ai.chat.candidates[0].priority=100",
                    "app.ai.rerank.default-model=qwen3-vl-rerank",
                    "app.ai.rerank.candidates[0].id=qwen3-vl-rerank",
                    "app.ai.rerank.candidates[0].provider=bailian",
                    "app.ai.rerank.candidates[0].model=qwen3-vl-rerank",
                    "app.ai.rerank.candidates[0].priority=100",
                    "app.ai.embedding.default-model=text-embedding-v4",
                    "app.ai.embedding.candidates[0].id=text-embedding-v4",
                    "app.ai.embedding.candidates[0].provider=bailian",
                    "app.ai.embedding.candidates[0].model=text-embedding-v4",
                    "app.ai.embedding.candidates[0].dimension=1024",
                    "app.ai.circuit-breaker.failure-threshold=3",
                    "app.ai.circuit-breaker.open-duration-seconds=60",
                    "app.ai.circuit-breaker.half-open-max-probes=1");

    @Test
    @DisplayName("应该根据配置创建路由 ChatModel")
    void shouldCreateRoutingChatModelFromAiProperties() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(ChatModel.class);
            assertThat(context).hasSingleBean(RoutingChatModel.class);
            assertThat(context).hasSingleBean(OpenAiCompatibleRerankModel.class);
        });
    }

    @Test
    @DisplayName("应该根据配置创建 EmbeddingModel")
    void shouldCreateEmbeddingModelFromAiProperties() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(EmbeddingModel.class);
            assertThat(context.getBean(EmbeddingModel.class).dimensions()).isEqualTo(1024);
        });
    }

    @Configuration
    @EnableConfigurationProperties(AiProperties.class)
    static class TestConfiguration {
    }
}
