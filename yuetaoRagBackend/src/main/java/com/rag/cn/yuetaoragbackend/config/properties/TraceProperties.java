package com.rag.cn.yuetaoragbackend.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author zrq
 * 2026/04/22 12:00
 */
@Data
@ConfigurationProperties(prefix = "app.trace")
public class TraceProperties {

    /** 是否启用链路追踪。 */
    private Boolean enabled;

    /** 是否记录链路载荷详情。 */
    private Boolean logPayload;

    /** 追踪日志保留天数。 */
    private Integer retainDays;
}
