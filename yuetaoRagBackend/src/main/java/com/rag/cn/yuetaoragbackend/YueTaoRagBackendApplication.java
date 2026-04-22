package com.rag.cn.yuetaoragbackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * @author zrq
 * 2026/04/22 12:00
 */
@EnableAspectJAutoProxy
@SpringBootApplication
@ConfigurationPropertiesScan
public class YueTaoRagBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(YueTaoRagBackendApplication.class, args);
    }

}
