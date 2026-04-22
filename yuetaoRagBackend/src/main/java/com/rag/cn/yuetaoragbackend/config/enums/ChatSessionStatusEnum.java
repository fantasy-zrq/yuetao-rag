package com.rag.cn.yuetaoragbackend.config.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * @author zrq
 * 2026/04/22 16:20
 */
@Getter
@RequiredArgsConstructor
public enum ChatSessionStatusEnum {

    /** 活跃。 */
    ACTIVE("ACTIVE"),

    /** 关闭。 */
    CLOSED("CLOSED"),

    /** 归档。 */
    ARCHIVED("ARCHIVED");

    /** 状态编码。 */
    private final String code;
}
