package com.rag.cn.yuetaoragbackend.mq.support;

import cn.hutool.crypto.digest.DigestUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.rag.cn.yuetaoragbackend.mq.MessageWrapper;
import com.rag.cn.yuetaoragbackend.mq.event.KnowledgeDocumentSplitEvent;
import java.nio.charset.StandardCharsets;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.util.StringUtils;

/**
 * 解析文档切片消息的幂等键。
 * 正常情况下直接使用 chunkLogId；如果消息体异常，则回退到消息键和消息体摘要，避免幂等切面先于业务解析时报错。
 */
public final class KnowledgeDocumentSplitConsumeKeyHelper {

    private static final TypeReference<MessageWrapper<KnowledgeDocumentSplitEvent>> SPLIT_EVENT_TYPE =
            new TypeReference<>() {
            };

    private KnowledgeDocumentSplitConsumeKeyHelper() {
    }

    public static String buildKey(MessageExt messageExt) {
        if (messageExt == null) {
            return "null-message";
        }
        MessageWrapper<KnowledgeDocumentSplitEvent> wrapper = parseWrapper(messageExt);
        if (wrapper != null
                && wrapper.getBody() != null
                && wrapper.getBody().getChunkLogId() != null) {
            return String.valueOf(wrapper.getBody().getChunkLogId());
        }
        String bodyDigest = bodyDigest(messageExt);
        if (StringUtils.hasText(messageExt.getKeys())) {
            return messageExt.getKeys() + ":" + bodyDigest;
        }
        return bodyDigest;
    }

    private static MessageWrapper<KnowledgeDocumentSplitEvent> parseWrapper(MessageExt messageExt) {
        try {
            return JSON.parseObject(new String(messageExt.getBody(), StandardCharsets.UTF_8), SPLIT_EVENT_TYPE);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String bodyDigest(MessageExt messageExt) {
        byte[] body = messageExt.getBody();
        return body == null || body.length == 0 ? "empty-body" : DigestUtil.sha256Hex(body);
    }
}
