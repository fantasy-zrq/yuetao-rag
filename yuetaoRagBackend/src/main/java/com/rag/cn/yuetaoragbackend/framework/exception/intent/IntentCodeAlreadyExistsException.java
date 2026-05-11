package com.rag.cn.yuetaoragbackend.framework.exception.intent;

import com.rag.cn.yuetaoragbackend.framework.exception.ClientException;

/**
 * @author zrq
 * 2026/05/11
 */
public class IntentCodeAlreadyExistsException extends ClientException {

    public IntentCodeAlreadyExistsException(String intentCode) {
        super("意图标识已存在：" + intentCode);
    }
}
