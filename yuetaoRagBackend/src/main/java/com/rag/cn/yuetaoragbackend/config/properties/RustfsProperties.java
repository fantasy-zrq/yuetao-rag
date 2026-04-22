package com.rag.cn.yuetaoragbackend.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author zrq
 * 2026/04/22 12:00
 */
@Data
@ConfigurationProperties(prefix = "rustfs")
public class RustfsProperties {

    /**
     * RustFS 服务访问地址。
     */
    private String url;

    /**
     * RustFS 访问密钥 ID。
     */
    private String accessKeyId;

    /**
     * RustFS 访问密钥 Secret。
     */
    private String secretAccessKey;

    /**
     * 文档默认存储的 bucket 名称。
     */
    private String bucket;

    /**
     * 文档对外访问基础地址。
     */
    private String publicBaseUrl;
}
