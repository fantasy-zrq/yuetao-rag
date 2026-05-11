package com.rag.cn.yuetaoragbackend.framework.exception.intent;

import com.rag.cn.yuetaoragbackend.framework.exception.ClientException;

/**
 * @author zrq
 * 2026/05/11
 */
public class IntentNodeHasChildrenException extends ClientException {

    public IntentNodeHasChildrenException(Long id) {
        super("该节点下存在子节点，无法直接删除：" + id);
    }
}
