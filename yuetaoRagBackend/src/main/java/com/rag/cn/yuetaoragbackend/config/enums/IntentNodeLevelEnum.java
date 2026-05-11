package com.rag.cn.yuetaoragbackend.config.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * @author zrq
 * 2026/05/11
 */
@Getter
@RequiredArgsConstructor
public enum IntentNodeLevelEnum {

    /** 顶层领域。 */
    DOMAIN(0, "顶层领域"),

    /** 业务分类。 */
    CATEGORY(1, "业务分类"),

    /** 具体主题。 */
    TOPIC(2, "具体主题");

    private final Integer code;
    private final String desc;
}
