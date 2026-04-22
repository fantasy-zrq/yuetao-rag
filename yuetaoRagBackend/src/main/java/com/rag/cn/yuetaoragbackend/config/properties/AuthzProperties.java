package com.rag.cn.yuetaoragbackend.config.properties;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author zrq
 * 2026/04/22 12:00
 */
@Data
@ConfigurationProperties(prefix = "app.authz")
public class AuthzProperties {

    /**
     * 具备管理员权限的角色编码列表。
     */
    private List<String> adminRoleCodes = new ArrayList<>();

    /**
     * 文档默认最低访问职级。
     */
    private Integer defaultDocumentMinRank;
}
