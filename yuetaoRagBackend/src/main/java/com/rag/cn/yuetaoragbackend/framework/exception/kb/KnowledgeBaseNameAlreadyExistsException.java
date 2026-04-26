package com.rag.cn.yuetaoragbackend.framework.exception.kb;

import com.rag.cn.yuetaoragbackend.framework.exception.ClientException;

/**
 * @author zrq
 * 2026/04/26 10:30
 */
public class KnowledgeBaseNameAlreadyExistsException extends ClientException {

    public KnowledgeBaseNameAlreadyExistsException(String name) {
        super("知识库名称已存在：" + name);
    }
}
