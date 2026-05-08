package com.rag.cn.yuetaoragbackend.service.impl;

import reactor.core.publisher.Flux;

/**
 * 深度思考流式内容提供者策略接口。
 * 根据候选模型ID和用户问题，返回包含思考过程和回答内容的响应式流。
 */
@FunctionalInterface
public interface CandidateThinkingStreamProvider {

    /**
     * 以流式方式生成包含思考过程的回答内容。
     *
     * @param candidateId 候选模型ID
     * @param question    用户问题
     * @return 包含思考过程和回答内容的响应式流
     */
    Flux<StreamContent> stream(String candidateId, String question);
}
