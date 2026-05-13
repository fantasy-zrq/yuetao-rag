package com.rag.cn.yuetaoragbackend.service.support.chat;

import com.rag.cn.yuetaoragbackend.config.record.StreamContentRecord;
import reactor.core.publisher.Flux;

/**
 * 深度思考流式内容提供者策略接口。
 */
@FunctionalInterface
public interface CandidateThinkingStreamProvider {

    /**
     * 以流式方式生成包含思考过程的回答内容。
     */
    Flux<StreamContentRecord> stream(String candidateId, String question);
}
