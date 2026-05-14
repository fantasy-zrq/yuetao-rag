package com.rag.cn.yuetaoragbackend.framework.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;

class DataBaseConfigurationTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(DataBaseConfiguration.class)
            .withBean(JdbcTemplate.class, () -> org.mockito.Mockito.mock(JdbcTemplate.class));

    @Test
    void shouldNotRegisterIntentNodeSchemaPatchRunnerByDefault() {
        contextRunner.run(context -> assertThat(context).doesNotHaveBean("intentNodeSchemaPatchRunner"));
    }

    @Test
    void shouldRegisterIntentNodeSchemaPatchRunnerWhenExplicitlyEnabled() {
        contextRunner.withPropertyValues("app.database.intent-node-schema-patch-enabled=true")
                .run(context -> assertThat(context).hasBean("intentNodeSchemaPatchRunner"));
    }
}
