package com.rag.cn.yuetaoragbackend.framework.exception.kb;

import com.rag.cn.yuetaoragbackend.framework.exception.ClientException;

/**
 * @author zrq
 * 2026/04/26 10:30
 */
public class KnowledgeBaseHasDocumentsException extends ClientException {

    public KnowledgeBaseHasDocumentsException(Long knowledgeBaseId) {
        super("知识库仍有文档，禁止删除：" + knowledgeBaseId);
    }
}
