package com.rag.cn.yuetaoragbackend.service.support.chat;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 维护一个活动中的 SSE 对话流及其订阅句柄，便于显式停止或客户端断开时统一释放。
 */
public final class ChatActiveStream {

    private final Long sessionId;
    private final Long userId;
    private final SseEmitter emitter;
    private final AtomicReference<Disposable> subscriptionRef;
    private final AtomicBoolean closed;

    public ChatActiveStream(Long sessionId, Long userId, SseEmitter emitter,
                            AtomicReference<Disposable> subscriptionRef, AtomicBoolean closed) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.emitter = emitter;
        this.subscriptionRef = subscriptionRef;
        this.closed = closed;
    }

    public Long sessionId() {
        return sessionId;
    }

    public Long userId() {
        return userId;
    }

    public SseEmitter emitter() {
        return emitter;
    }

    public void cancel() {
        closed.set(true);
        Disposable subscription = subscriptionRef.get();
        if (subscription != null && !subscription.isDisposed()) {
            subscription.dispose();
        }
    }
}
