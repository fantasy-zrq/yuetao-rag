package com.rag.cn.yuetaoragbackend.config.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * @author zrq
 * 2026/04/28 00:30
 */
@Getter
@RequiredArgsConstructor
public enum DocumentChunkLogOperationTypeEnum {

    /** 首次分块。 */
    SPLIT("SPLIT"),

    /** 重建分块。 */
    REBUILD("REBUILD");

    /** 操作类型编码。 */
    private final String code;
}
