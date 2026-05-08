package com.rag.cn.yuetaoragbackend.service.impl;

import reactor.core.publisher.Flux;

/**
 * 普通流式内容提供者策略接口。
 * 根据候选模型ID和用户问题，返回内容增量的响应式流。
 */
@FunctionalInterface
public interface CandidateStreamProvider {

    /**
     * 以流式方式生成回答内容。
     *
     * @param candidateId 候选模型ID
     * @param question    用户问题
     * @return 内容增量的响应式流
     */
    Flux<String> stream(String candidateId, String question);
}
