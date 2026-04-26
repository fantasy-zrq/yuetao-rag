package com.rag.cn.yuetaoragbackend.framework.exception.kb;

import com.rag.cn.yuetaoragbackend.framework.exception.ClientException;

/**
 * @author zrq
 * 2026/04/26 10:30
 */
public class KnowledgeBaseNotFoundException extends ClientException {

    public KnowledgeBaseNotFoundException(Long knowledgeBaseId) {
        super("知识库不存在或已删除：" + knowledgeBaseId);
    }
}
