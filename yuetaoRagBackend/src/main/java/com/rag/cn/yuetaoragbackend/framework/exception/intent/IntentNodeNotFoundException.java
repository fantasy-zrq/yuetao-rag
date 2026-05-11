package com.rag.cn.yuetaoragbackend.framework.exception.intent;

import com.rag.cn.yuetaoragbackend.framework.exception.ClientException;

/**
 * @author zrq
 * 2026/05/11
 */
public class IntentNodeNotFoundException extends ClientException {

    public IntentNodeNotFoundException(Long id) {
        super("意图节点不存在或已删除：" + id);
    }
}
