package com.rag.cn.yuetaoragbackend.config.properties;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.env.PropertySource;

/**
 * @author zrq
 * 2026/04/22 12:00
 */
class ConfigurationPropertiesBindingTests {

    @Test
    void shouldBindApplicationDevYamlToPropertiesObjects() throws IOException {
        ConfigurableEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test-secrets", java.util.Map.of(
            "ALIYUN_ACCESS_KEY_ID", "aliyun-test-key",
            "SILICONFLOW_API_KEY", "siliconflow-test-key"
        )));
        loadYaml(environment, "application-dev.yaml");

        RustfsProperties rustfsProperties = Binder.get(environment)
            .bind("rustfs", Bindable.of(RustfsProperties.class))
            .orElseThrow(() -> new IllegalStateException("Failed to bind rustfs properties"));
        AiProperties aiProperties = Binder.get(environment)
            .bind("app.ai", Bindable.of(AiProperties.class))
            .orElseThrow(() -> new IllegalStateException("Failed to bind app.ai properties"));
        RagRetrievalProperties retrievalProperties = Binder.get(environment)
            .bind("app.rag.retrieval", Bindable.of(RagRetrievalProperties.class))
            .orElseThrow(() -> new IllegalStateException("Failed to bind app.rag.retrieval properties"));
        MemoryProperties memoryProperties = Binder.get(environment)
            .bind("app.memory", Bindable.of(MemoryProperties.class))
            .orElseThrow(() -> new IllegalStateException("Failed to bind app.memory properties"));
        AuthzProperties authzProperties = Binder.get(environment)
            .bind("app.authz", Bindable.of(AuthzProperties.class))
            .orElseThrow(() -> new IllegalStateException("Failed to bind app.authz properties"));
        AuthProperties authProperties = Binder.get(environment)
            .bind("app.auth", Bindable.of(AuthProperties.class))
            .orElseThrow(() -> new IllegalStateException("Failed to bind app.auth properties"));
        TraceProperties traceProperties = Binder.get(environment)
            .bind("app.trace", Bindable.of(TraceProperties.class))
            .orElseThrow(() -> new IllegalStateException("Failed to bind app.trace properties"));

        assertThat(rustfsProperties.getBucket()).isEqualTo("yuetao-rag-docs");
        assertThat(rustfsProperties.getPublicBaseUrl()).isEqualTo("http://172.18.58.216:19000/yuetao-rag-docs");

        assertThat(aiProperties.getProviders()).containsKeys("bailian", "siliconflow");
        assertThat(aiProperties.getProviders().get("bailian").getApiKey()).isEqualTo("aliyun-test-key");
        assertThat(aiProperties.getChat().getDefaultModel()).isEqualTo("qwen-plus");
        assertThat(aiProperties.getChat().getDeepThinkingModel()).isEqualTo("qwen-plus");
        assertThat(aiProperties.getChat().getCandidates()).hasSize(3);
        assertThat(aiProperties.getChat().getCandidates().get(0).getProvider()).isEqualTo("bailian");
        assertThat(aiProperties.getEmbedding().getDefaultModel()).isEqualTo("text-embedding-v4");
        assertThat(aiProperties.getRerank().getDefaultModel()).isEqualTo("qwen3-vl-rerank");
        assertThat(aiProperties.getCircuitBreaker().getFailureThreshold()).isEqualTo(3);
        assertThat(aiProperties.getCircuitBreaker().getStreamChunkIdleTimeoutMillis()).isEqualTo(5000);

        assertThat(retrievalProperties.getTopK()).isEqualTo(8);
        assertThat(memoryProperties.getRecentWindowSize()).isEqualTo(30);
        assertThat(authzProperties.getAdminRoleCodes()).isEqualTo(List.of("ADMIN"));
        assertThat(authProperties.getTokenSessionLoginUserKey()).isEqualTo("loginUser");
        assertThat(authProperties.getRememberMeActiveTimeoutSeconds()).isEqualTo(604800L);
        assertThat(authProperties.getRememberMeTimeoutSeconds()).isEqualTo(2592000L);
        assertThat(authProperties.getSessionActiveTimeoutSeconds()).isEqualTo(43200L);
        assertThat(authProperties.getSessionTimeoutSeconds()).isEqualTo(43200L);
        assertThat(traceProperties.getEnabled()).isTrue();

        assertThat(environment.getProperty("sa-token.token-name")).isEqualTo("Authorization");
        assertThat(environment.getProperty("sa-token.is-read-header")).isEqualTo("true");
        assertThat(environment.getProperty("sa-token.is-read-cookie")).isEqualTo("false");
        assertThat(environment.getProperty("sa-token.dynamic-active-timeout")).isEqualTo("true");
        assertThat(environment.getProperty("sa-token.auto-renew")).isEqualTo("true");
        assertThat(environment.getProperty("sa-token.timeout")).isEqualTo("2592000");
    }

    private void loadYaml(ConfigurableEnvironment environment, String location) throws IOException {
        Resource resource = new ClassPathResource(location);
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        List<PropertySource<?>> sources = loader.load(location, resource);
        for (PropertySource<?> source : sources) {
            environment.getPropertySources().addLast(source);
        }
    }
}
