package com.rag.cn.yuetaoragbackend.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 认证链路配置。
 * 这里仅放会直接影响登录态生命周期和 tokenSession 存储结构的参数。
 */
@Data
@ConfigurationProperties(prefix = "app.auth")
public class AuthProperties {

    /**
     * tokenSession 中存放登录用户快照的 key。
     */
    private String tokenSessionLoginUserKey;

    /**
     * 勾选“7天免登录”时，连续无访问多久失效。
     */
    private Long rememberMeActiveTimeoutSeconds;

    /**
     * 勾选“7天免登录”时，token 的最长硬过期时间。
     */
    private Long rememberMeTimeoutSeconds;

    /**
     * 未勾选“7天免登录”时，连续无访问多久失效。
     */
    private Long sessionActiveTimeoutSeconds;

    /**
     * 未勾选“7天免登录”时，token 的最长硬过期时间。
     */
    private Long sessionTimeoutSeconds;
}
