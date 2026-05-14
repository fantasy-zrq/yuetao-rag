package com.rag.cn.yuetaoragbackend.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.rag.cn.yuetaoragbackend.framework.context.LoginUser;
import com.rag.cn.yuetaoragbackend.framework.context.UserContext;
import com.rag.cn.yuetaoragbackend.config.properties.AuthProperties;
import com.rag.cn.yuetaoragbackend.framework.config.WebAutoConfiguration;
import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class WebAutoConfigurationTests {

    @Test
    void shouldCreateBoundedChatStreamExecutor() {
        WebAutoConfiguration configuration = new WebAutoConfiguration(new AuthProperties());

        ExecutorService executorService = configuration.chatStreamExecutor();
        try {
            ThreadPoolExecutor executor = extractThreadPoolExecutor(executorService);
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

    @Test
    void shouldTransmitUserContextIntoChatStreamExecutorTasks() throws InterruptedException {
        WebAutoConfiguration configuration = new WebAutoConfiguration(new AuthProperties());
        ExecutorService executorService = configuration.chatStreamExecutor();
        ThreadPoolExecutor executor = extractThreadPoolExecutor(executorService);
        CountDownLatch finished = new CountDownLatch(1);
        AtomicReference<String> userIdRef = new AtomicReference<>();
        try {
            UserContext.clear();
            executor.prestartAllCoreThreads();
            UserContext.set(LoginUser.builder().userId("12345").build());
            executorService.execute(() -> {
                userIdRef.set(UserContext.getUserId());
                finished.countDown();
            });
            assertThat(finished.await(3, TimeUnit.SECONDS)).isTrue();
            executorService.shutdown();
            assertThat(executorService.awaitTermination(3, TimeUnit.SECONDS)).isTrue();
            assertThat(userIdRef.get()).isEqualTo("12345");
        } finally {
            UserContext.clear();
            executorService.shutdownNow();
        }
    }

    private ThreadPoolExecutor extractThreadPoolExecutor(ExecutorService executorService) {
        if (executorService instanceof ThreadPoolExecutor threadPoolExecutor) {
            return threadPoolExecutor;
        }
        for (Field field : executorService.getClass().getDeclaredFields()) {
            field.setAccessible(true);
            try {
                Object value = field.get(executorService);
                if (value instanceof ThreadPoolExecutor threadPoolExecutor) {
                    return threadPoolExecutor;
                }
                if (value instanceof ExecutorService nestedExecutorService && value != executorService) {
                    ThreadPoolExecutor nested = extractThreadPoolExecutor(nestedExecutorService);
                    if (nested != null) {
                        return nested;
                    }
                }
            } catch (IllegalAccessException ignored) {
            }
        }
        throw new IllegalStateException("未找到底层 ThreadPoolExecutor");
    }
}
