package com.rag.cn.yuetaoragbackend.config.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * @author zrq
 * 2026/04/22 16:20
 */
@Getter
@RequiredArgsConstructor
public enum VisibilityScopeEnum {

    /** 内部可见。 */
    INTERNAL("INTERNAL"),

    /** 敏感可见。 */
    SENSITIVE("SENSITIVE");

    /** 可见范围编码。 */
    private final String code;
}
