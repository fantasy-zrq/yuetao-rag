package com.rag.cn.yuetaoragbackend.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.rag.cn.yuetaoragbackend.framework.config.WebAutoConfiguration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class WebAutoConfigurationTests {

    @Test
    void shouldCreateBoundedChatStreamExecutor() {
        WebAutoConfiguration configuration = new WebAutoConfiguration();

        ExecutorService executorService = configuration.chatStreamExecutor();
        try {
            assertThat(executorService).isInstanceOf(ThreadPoolExecutor.class);
            ThreadPoolExecutor executor = (ThreadPoolExecutor) executorService;
            assertThat(executor.getCorePoolSize()).isEqualTo(4);
            assertThat(executor.getMaximumPoolSize()).isEqualTo(32);
            assertThat(executor.getKeepAliveTime(TimeUnit.SECONDS)).isEqualTo(60L);
            assertThat(executor.getQueue().remainingCapacity()).isEqualTo(128);
            assertThat(executor.getRejectedExecutionHandler())
                    .isInstanceOf(ThreadPoolExecutor.CallerRunsPolicy.class);
        } finally {
            executorService.shutdownNow();
        }
    }
}
