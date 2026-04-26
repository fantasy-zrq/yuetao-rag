package com.rag.cn.yuetaoragbackend.framework.exception.kb;

import com.rag.cn.yuetaoragbackend.framework.exception.ClientException;

/**
 * @author zrq
 * 2026/04/26 10:30
 */
public class KnowledgeBaseCollectionNameAlreadyExistsException extends ClientException {

    public KnowledgeBaseCollectionNameAlreadyExistsException(String collectionName) {
        super("知识库 collectionName 已存在：" + collectionName);
    }
}
