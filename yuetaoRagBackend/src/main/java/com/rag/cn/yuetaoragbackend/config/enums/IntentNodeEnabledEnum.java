package com.rag.cn.yuetaoragbackend.config.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * @author zrq
 * 2026/05/11
 */
@Getter
@RequiredArgsConstructor
public enum IntentNodeEnabledEnum {

    /** 启用。 */
    ENABLED(1),

    /** 停用。 */
    DISABLED(0);

    private final Integer code;
}
