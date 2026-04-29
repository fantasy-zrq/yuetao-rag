package com.rag.cn.yuetaoragbackend.config.record;

import com.rag.cn.yuetaoragbackend.config.enums.ChatModelCircuitStateEnum;

/**
 * @author zrq
 * 2026/04/29 19:30
 */
public record ChatModelRuntimeInfoRecord(String provider, String modelName, String candidateId,
                                         ChatModelCircuitStateEnum state) {
}
