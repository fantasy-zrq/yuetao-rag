package com.rag.cn.yuetaoragbackend.config.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * @author zrq
 * 2026/04/22 16:20
 */
@Getter
@RequiredArgsConstructor
public enum ChatMessageContentTypeEnum {

    /** 普通文本。 */
    TEXT("TEXT"),

    /** 思考内容。 */
    THINKING("THINKING"),

    /** 事件消息。 */
    EVENT("EVENT");

    /** 内容类型编码。 */
    private final String code;
}
