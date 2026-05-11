package com.rag.cn.yuetaoragbackend.config.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * @author zrq
 * 2026/05/11
 */
@Getter
@RequiredArgsConstructor
public enum IntentNodeKindEnum {

    /** 知识库检索。 */
    KB(0, "知识库检索"),

    /** 系统交互。 */
    SYSTEM(1, "系统交互"),

    /** 工具调用。 */
    MCP(2, "工具调用");

    private final Integer code;
    private final String desc;
}
