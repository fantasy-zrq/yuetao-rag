package com.rag.cn.yuetaoragbackend.config.record;

/**
 * 流式模型响应的内容载体，区分思考过程和最终回答。
 *
 * @param thinking 模型的推理/思考过程文本，深度思考模式下非空
 * @param content  模型的最终回答文本
 */
public record StreamContentRecord(String thinking, String content) {

    public boolean hasThinking() {
        return thinking != null && !thinking.isEmpty();
    }

    public boolean hasContent() {
        return content != null && !content.isEmpty();
    }
}
