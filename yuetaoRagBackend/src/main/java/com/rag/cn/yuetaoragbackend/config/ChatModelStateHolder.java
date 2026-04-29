package com.rag.cn.yuetaoragbackend.config;

import com.rag.cn.yuetaoragbackend.config.enums.ChatModelCircuitStateEnum;
import com.rag.cn.yuetaoragbackend.config.properties.AiProperties;
import java.time.Instant;
import lombok.Getter;

/**
 * @author zrq
 * 2026/04/29 19:30
 */
@Getter
public class ChatModelStateHolder {

    private ChatModelCircuitStateEnum state = ChatModelCircuitStateEnum.CLOSED;
    private int consecutiveFailures = 0;
    private Instant openUntil;
    private int halfOpenProbeCount = 0;

    public synchronized boolean tryAcquire(AiProperties.CircuitBreakerProperties properties) {
        if (state == ChatModelCircuitStateEnum.CLOSED) {
            return true;
        }
        if (state == ChatModelCircuitStateEnum.OPEN) {
            if (openUntil != null && Instant.now().isAfter(openUntil)) {
                state = ChatModelCircuitStateEnum.HALF_OPEN;
                halfOpenProbeCount = 0;
            } else {
                return false;
            }
        }
        if (state == ChatModelCircuitStateEnum.HALF_OPEN) {
            int maxProbeCount = safeInt(properties.getHalfOpenMaxProbes(), 1);
            if (halfOpenProbeCount >= maxProbeCount) {
                return false;
            }
            halfOpenProbeCount++;
            return true;
        }
        return true;
    }

    public synchronized void onSuccess() {
        state = ChatModelCircuitStateEnum.CLOSED;
        consecutiveFailures = 0;
        openUntil = null;
        halfOpenProbeCount = 0;
    }

    public synchronized void onFailure(AiProperties.CircuitBreakerProperties properties) {
        if (state == ChatModelCircuitStateEnum.HALF_OPEN) {
            open(properties);
            return;
        }
        consecutiveFailures++;
        int threshold = safeInt(properties.getFailureThreshold(), 3);
        if (consecutiveFailures >= threshold) {
            open(properties);
        }
    }

    private void open(AiProperties.CircuitBreakerProperties properties) {
        state = ChatModelCircuitStateEnum.OPEN;
        consecutiveFailures = 0;
        halfOpenProbeCount = 0;
        openUntil = Instant.now().plusSeconds(safeInt(properties.getOpenDurationSeconds(), 60));
    }

    private int safeInt(Integer value, int defaultValue) {
        return value == null || value <= 0 ? defaultValue : value;
    }
}
