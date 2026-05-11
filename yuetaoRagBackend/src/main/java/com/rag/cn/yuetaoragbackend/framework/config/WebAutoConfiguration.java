package com.rag.cn.yuetaoragbackend.framework.config;


import com.rag.cn.yuetaoragbackend.framework.web.GlobalExceptionHandler;
import com.rag.cn.yuetaoragbackend.framework.web.UserContextInterceptor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 组件自动装配
 */
@Configuration
public class WebAutoConfiguration implements WebMvcConfigurer {

    /**
     * 构建全局异常拦截器组件 Bean
     */
    @Bean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }

    @Bean(destroyMethod = "shutdown")
    public ExecutorService chatStreamExecutor() {
        return new ThreadPoolExecutor(
                4,
                32,
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(128),
                new ThreadPoolExecutor.CallerRunsPolicy());
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new UserContextInterceptor())
                .addPathPatterns("/**");
    }
}
