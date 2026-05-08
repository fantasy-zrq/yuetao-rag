package com.rag.cn.yuetaoragbackend.service.impl;

/**
 * 流式模型响应的内容载体，区分思考过程和最终回答。
 *
 * @param thinking 模型的推理/思考过程文本，深度思考模式下非空
 * @param content  模型的最终回答文本
 */
public record StreamContent(String thinking, String content) {

    /**
     * 是否包含思考过程内容。
     */
    public boolean hasThinking() {
        return thinking != null && !thinking.isEmpty();
    }

    /**
     * 是否包含回答内容。
     */
    public boolean hasContent() {
        return content != null && !content.isEmpty();
    }
}
