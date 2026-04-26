package com.rag.cn.yuetaoragbackend.framework.exception.kb;

import com.rag.cn.yuetaoragbackend.framework.exception.ClientException;

/**
 * @author zrq
 * 2026/04/26 10:30
 */
public class InvalidKnowledgeBaseCollectionNameException extends ClientException {

    public InvalidKnowledgeBaseCollectionNameException(String collectionName) {
        super("collectionName 不合法：" + collectionName);
    }
}
