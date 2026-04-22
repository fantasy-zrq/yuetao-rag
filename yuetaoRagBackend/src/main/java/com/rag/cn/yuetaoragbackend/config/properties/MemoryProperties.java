package com.rag.cn.yuetaoragbackend.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author zrq
 * 2026/04/22 12:00
 */
@Data
@ConfigurationProperties(prefix = "app.memory")
public class MemoryProperties {

    /** 最近轮次消息保留窗口大小。 */
    private Integer recentWindowSize;

    /** 触发摘要压缩的消息阈值。 */
    private Integer summaryTriggerThreshold;

    /** 单次摘要允许的最大 token 数。 */
    private Integer summaryMaxTokens;

    /** 会话记忆在 Redis 中的缓存过期时间，单位秒。 */
    private Integer redisCacheTtlSeconds;
}
