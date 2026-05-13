package com.rag.cn.yuetaoragbackend;

import static org.assertj.core.api.Assertions.assertThat;

import cn.dev33.satoken.SaManager;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "app.ai.providers.bailian.api-key=test-bailian-key",
        "app.ai.embedding.default-model=text-embedding-v4",
        "app.ai.embedding.candidates[0].id=text-embedding-v4",
        "app.ai.embedding.candidates[0].provider=bailian",
        "app.ai.embedding.candidates[0].model=text-embedding-v4",
        "app.ai.embedding.candidates[0].dimension=1024",
        "spring.ai.vectorstore.pgvector.initialize-schema=false"
})
class YueTaoRagBackendApplicationTests {

    @Test
    void contextLoads() {
    }

    @Test
    void shouldUseRedisBackedSaTokenDao() {
        assertThat(SaManager.getSaTokenDao().getClass().getName())
                .isEqualTo("cn.dev33.satoken.dao.SaTokenDaoForRedisTemplate");
    }

}
