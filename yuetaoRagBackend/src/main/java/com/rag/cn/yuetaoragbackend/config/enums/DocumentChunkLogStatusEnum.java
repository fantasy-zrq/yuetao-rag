package com.rag.cn.yuetaoragbackend.config.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * @author zrq
 * 2026/04/28 00:30
 */
@Getter
@RequiredArgsConstructor
public enum DocumentChunkLogStatusEnum {

    /** 处理中。 */
    PROCESSING("PROCESSING"),

    /** 执行成功。 */
    SUCCESS("SUCCESS"),

    /** 执行失败。 */
    FAILED("FAILED"),

    /** 执行超时。 */
    TIMEOUT("TIMEOUT");

    /** 状态编码。 */
    private final String code;
}
