package com.rag.cn.yuetaoragbackend.config;

import com.rag.cn.yuetaoragbackend.config.properties.RustfsProperties;

import java.net.URI;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

/**
 * @author zrq
 * 2026/04/26 10:30
 */
@Configuration
public class RustfsClientConfiguration {

    @Bean
    public S3Client rustfsS3Client(RustfsProperties rustfsProperties) {
        return S3Client.builder()
                .endpointOverride(URI.create(rustfsProperties.getUrl()))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
                        rustfsProperties.getAccessKeyId(),
                        rustfsProperties.getSecretAccessKey())))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();
    }
}
