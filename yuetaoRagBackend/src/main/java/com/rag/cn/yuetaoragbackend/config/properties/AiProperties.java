package com.rag.cn.yuetaoragbackend.config.properties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author zrq
 * 2026/04/22 12:00
 */
@Data
@ConfigurationProperties(prefix = "app.ai")
public class AiProperties {

    /**
     * AI 提供商配置映射，key 为 provider 名称。
     */
    private Map<String, ProviderProperties> providers = new LinkedHashMap<>();

    /**
     * 聊天模型路由配置。
     */
    private ChatProperties chat = new ChatProperties();

    /**
     * 向量模型路由配置。
     */
    private EmbeddingProperties embedding = new EmbeddingProperties();

    /**
     * 重排序模型路由配置。
     */
    private RerankProperties rerank = new RerankProperties();

    /**
     * 模型熔断器配置。
     */
    private CircuitBreakerProperties circuitBreaker = new CircuitBreakerProperties();

    /**
     * @author zrq
     * 2026/04/22 12:00
     */
    @Data
    public static class ProviderProperties {

        /**
         * 提供商基础地址。
         */
        private String url;

        /**
         * 提供商 API Key。
         */
        private String apiKey;

        /**
         * 提供商各类接口路径。
         */
        private EndpointsProperties endpoints = new EndpointsProperties();
    }

    /**
     * @author zrq
     * 2026/04/22 12:00
     */
    @Data
    public static class EndpointsProperties {

        /**
         * 聊天接口路径。
         */
        private String chat;

        /**
         * 向量接口路径。
         */
        private String embedding;

        /**
         * 重排序接口路径。
         */
        private String rerank;
    }

    /**
     * @author zrq
     * 2026/04/22 12:00
     */
    @Data
    public static class ChatProperties {

        /**
         * 默认聊天模型候选 ID。
         */
        private String defaultModel;

        /**
         * 深度思考场景下优先使用的聊天模型候选 ID。
         */
        private String deepThinkingModel;

        /**
         * 聊天模型候选列表。
         */
        private List<ChatCandidateProperties> candidates = new ArrayList<>();
    }

    /**
     * @author zrq
     * 2026/04/22 12:00
     */
    @Data
    public static class ChatCandidateProperties {

        /**
         * 候选模型唯一标识。
         */
        private String id;

        /**
         * 候选模型所属 provider 名称。
         */
        private String provider;

        /**
         * 候选模型是否启用。
         */
        private Boolean enabled;

        /**
         * 候选模型的真实模型名。
         */
        private String model;

        /**
         * 候选模型是否支持流式输出。
         */
        private Boolean streamingEnabled;

        /**
         * 候选模型是否支持深度思考。
         */
        private Boolean supportsThinking;

        /**
         * 候选模型优先级，数值越大优先级越高。
         */
        private Integer priority;
    }

    /**
     * @author zrq
     * 2026/04/22 12:00
     */
    @Data
    public static class EmbeddingProperties {

        /**
         * 默认向量模型候选 ID。
         */
        private String defaultModel;

        /**
         * 向量模型候选列表。
         */
        private List<EmbeddingCandidateProperties> candidates = new ArrayList<>();
    }

    /**
     * @author zrq
     * 2026/04/22 12:00
     */
    @Data
    public static class EmbeddingCandidateProperties {

        /**
         * 候选向量模型唯一标识。
         */
        private String id;

        /**
         * 候选向量模型所属 provider 名称。
         */
        private String provider;

        /**
         * 候选向量模型真实模型名。
         */
        private String model;

        /**
         * 候选向量模型维度。
         */
        private Integer dimension;

        /**
         * 候选向量模型优先级，数值越大优先级越高。
         */
        private Integer priority;
    }

    /**
     * @author zrq
     * 2026/04/22 12:00
     */
    @Data
    public static class RerankProperties {

        /**
         * 默认重排序模型候选 ID。
         */
        private String defaultModel;

        /**
         * 重排序结果返回数量。
         */
        private Integer topN;

        /**
         * 重排序模型候选列表。
         */
        private List<RerankCandidateProperties> candidates = new ArrayList<>();
    }

    /**
     * @author zrq
     * 2026/04/22 12:00
     */
    @Data
    public static class RerankCandidateProperties {

        /**
         * 候选重排序模型唯一标识。
         */
        private String id;

        /**
         * 候选重排序模型所属 provider 名称。
         */
        private String provider;

        /**
         * 候选重排序模型真实模型名。
         */
        private String model;

        /**
         * 候选重排序模型优先级，数值越大优先级越高。
         */
        private Integer priority;
    }

    /**
     * @author zrq
     * 2026/04/22 12:00
     */
    @Data
    public static class CircuitBreakerProperties {

        /**
         * 触发熔断前允许的连续失败次数。
         */
        private Integer failureThreshold;

        /**
         * 熔断打开后的持续时间，单位秒。
         */
        private Integer openDurationSeconds;

        /**
         * 半开状态下允许的探测次数。
         */
        private Integer halfOpenMaxProbes;

        /**
         * 流式首包超时时间，单位毫秒。
         */
        private Integer firstTokenTimeoutMillis;
    }
}
