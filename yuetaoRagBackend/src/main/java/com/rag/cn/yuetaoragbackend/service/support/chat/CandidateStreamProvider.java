package com.rag.cn.yuetaoragbackend.service.support.chat;

import reactor.core.publisher.Flux;

/**
 * 普通流式内容提供者策略接口。
 */
@FunctionalInterface
public interface CandidateStreamProvider {

    /**
     * 以流式方式生成回答内容。
     */
    Flux<String> stream(String candidateId, String question);
}
