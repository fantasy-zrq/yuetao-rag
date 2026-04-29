package com.rag.cn.yuetaoragbackend.config.record;

import com.rag.cn.yuetaoragbackend.config.ChatModelStateHolder;
import com.rag.cn.yuetaoragbackend.config.enums.ChatModelCircuitStateEnum;
import com.rag.cn.yuetaoragbackend.config.properties.AiProperties;
import org.springframework.ai.chat.model.ChatModel;

/**
 * @author zrq
 * 2026/04/29 19:30
 */
public record ChatModelCandidateRuntimeRecord(String id, String provider, String modelName, Integer priority,
                                              ChatModel chatModel, ChatModelStateHolder stateHolder) {

    public boolean tryAcquire(AiProperties.CircuitBreakerProperties properties) {
        return stateHolder.tryAcquire(properties);
    }

    public void onSuccess() {
        stateHolder.onSuccess();
    }

    public void onFailure(AiProperties.CircuitBreakerProperties properties) {
        stateHolder.onFailure(properties);
    }

    public ChatModelCircuitStateEnum state() {
        return stateHolder.getState();
    }
}
