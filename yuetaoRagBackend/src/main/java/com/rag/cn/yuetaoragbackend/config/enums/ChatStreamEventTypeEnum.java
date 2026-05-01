package com.rag.cn.yuetaoragbackend.config.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * @author zrq
 * 2026/04/29 23:30
 */
@Getter
@RequiredArgsConstructor
public enum ChatStreamEventTypeEnum {

    MESSAGE_START("message_start"),
    DELTA("delta"),
    CITATION("citation"),
    RESET("reset"),
    MESSAGE_END("message_end"),
    ERROR("error");

    private final String code;
}
