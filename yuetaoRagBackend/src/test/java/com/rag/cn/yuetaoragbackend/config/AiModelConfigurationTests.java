package com.rag.cn.yuetaoragbackend.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.rag.cn.yuetaoragbackend.config.properties.AiProperties;
import org.junit.jupiter.api.Test;
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
                    "app.ai.providers.bailian.endpoints.embedding=/compatible-mode/v1/embeddings",
                    "app.ai.embedding.default-model=text-embedding-v4",
                    "app.ai.embedding.candidates[0].id=text-embedding-v4",
                    "app.ai.embedding.candidates[0].provider=bailian",
                    "app.ai.embedding.candidates[0].model=text-embedding-v4",
                    "app.ai.embedding.candidates[0].dimension=1024");

    @Test
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
