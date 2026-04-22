package com.rag.cn.yuetaoragbackend.framework.config;


import com.rag.cn.yuetaoragbackend.framework.web.GlobalExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Web 组件自动装配
 */
@Configuration
public class WebAutoConfiguration {

    /**
     * 构建全局异常拦截器组件 Bean
     */
    @Bean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }
}
