package com.rag.cn.yuetaoragbackend.config.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * @author zrq
 * 2026/04/22 16:20
 */
@Getter
@RequiredArgsConstructor
public enum DeleteFlagEnum {

    /** 未删除。 */
    NORMAL(0),

    /** 已删除。 */
    DELETED(1);

    /** 删除标记值。 */
    private final Integer code;
}
