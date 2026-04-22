package com.rag.cn.yuetaoragbackend.config.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * @author zrq
 * 2026/04/22 16:20
 */
@Getter
@RequiredArgsConstructor
public enum CommonStatusEnum {

    /** 启用。 */
    ENABLED("ENABLED"),

    /** 停用。 */
    DISABLED("DISABLED");

    /** 状态编码。 */
    private final String code;
}
