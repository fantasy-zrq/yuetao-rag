package com.rag.cn.yuetaoragbackend.config.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * @author zrq
 * 2026/04/22 16:20
 */
@Getter
@RequiredArgsConstructor
public enum ParseStatusEnum {

    /** 待解析。 */
    PENDING("PENDING"),

    /** 解析中。 */
    PROCESSING("PROCESSING"),

    /** 解析成功。 */
    SUCCESS("SUCCESS"),

    /** 解析失败。 */
    FAILED("FAILED");

    /** 状态编码。 */
    private final String code;
}
