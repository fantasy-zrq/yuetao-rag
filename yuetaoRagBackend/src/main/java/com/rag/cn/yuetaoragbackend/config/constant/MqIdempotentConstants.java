package com.rag.cn.yuetaoragbackend.config.constant;

/**
 * MQ 消费幂等常量。
 * 这里集中维护 Redis 幂等键前缀和有效期，避免重复消费策略散落在消费者实现中。
 */
public final class MqIdempotentConstants {

    private MqIdempotentConstants() {
    }

    /** 文档切片消息按 chunkLogId 去重，键保留 24 小时以覆盖 RocketMQ 延迟重投窗口。 */
    public static final String KNOWLEDGE_DOCUMENT_SPLIT_CONSUME_KEY_PREFIX =
            "mq:consume:knowledge-document-split:";

    /** 文档切片消费幂等键有效期，单位秒。 */
    public static final long KNOWLEDGE_DOCUMENT_SPLIT_CONSUME_KEY_TIMEOUT_SECONDS = 24L * 60L * 60L;
}
