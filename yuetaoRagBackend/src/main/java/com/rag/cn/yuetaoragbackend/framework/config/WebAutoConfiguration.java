package com.rag.cn.yuetaoragbackend.framework.config;


import com.alibaba.ttl.threadpool.TtlExecutors;
import com.rag.cn.yuetaoragbackend.config.properties.AuthProperties;
import com.rag.cn.yuetaoragbackend.framework.web.GlobalExceptionHandler;
import com.rag.cn.yuetaoragbackend.framework.web.UserContextInterceptor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 组件自动装配
 */
@Configuration
@RequiredArgsConstructor
public class WebAutoConfiguration implements WebMvcConfigurer {

    private static final int CHAT_STREAM_CORE_POOL_SIZE = 4;
    private static final int CHAT_STREAM_MAX_POOL_SIZE = 32;
    private static final long CHAT_STREAM_KEEP_ALIVE_SECONDS = 60L;
    private static final int CHAT_STREAM_QUEUE_CAPACITY = 128;

    private final AuthProperties authProperties;

    /**
     * 构建全局异常拦截器组件 Bean
     */
    @Bean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }

    @Bean(destroyMethod = "shutdown")
    public ExecutorService chatStreamExecutor() {
        // 流式对话在独立线程池中执行，必须在任务提交时捕获请求线程的 UserContext。
        return TtlExecutors.getTtlExecutorService(new ThreadPoolExecutor(
                CHAT_STREAM_CORE_POOL_SIZE,
                CHAT_STREAM_MAX_POOL_SIZE,
                CHAT_STREAM_KEEP_ALIVE_SECONDS,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(CHAT_STREAM_QUEUE_CAPACITY),
                new ThreadPoolExecutor.CallerRunsPolicy()));
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new UserContextInterceptor(authProperties))
                .addPathPatterns("/**");
    }
}
