package com.rag.cn.yuetaoragbackend.mq.consumer;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.rag.cn.yuetaoragbackend.mq.MessageWrapper;
import com.rag.cn.yuetaoragbackend.mq.event.KnowledgeDocumentSplitEvent;
import com.rag.cn.yuetaoragbackend.service.impl.KnowledgeDocumentSplitServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * @author zrq
 * 2026/04/27 10:20
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = KnowledgeDocumentSplitServiceImpl.SPLIT_TOPIC,
        consumerGroup = KnowledgeDocumentSplitServiceImpl.SPLIT_CONSUMER_GROUP
)
public class KnowledgeDocumentSplitConsumer implements RocketMQListener<MessageExt> {

    private final KnowledgeDocumentSplitServiceImpl splitExecutionService;

    @Override
    public void onMessage(MessageExt messageExt) {
        try {
            MessageWrapper<KnowledgeDocumentSplitEvent> wrapper = JSON.parseObject(
                    new String(messageExt.getBody(), StandardCharsets.UTF_8),
                    new TypeReference<MessageWrapper<KnowledgeDocumentSplitEvent>>() {
                    });
            // 处理文档切片，这里执行的是一个大事务，并且内部的事务都是单独的
            splitExecutionService.processSplit(wrapper.getBody().getDocumentId(), wrapper.getBody().getChunkLogId());
        } catch (Exception ex) {
            try {
                MessageWrapper<KnowledgeDocumentSplitEvent> wrapper = JSON.parseObject(
                        new String(messageExt.getBody(), StandardCharsets.UTF_8),
                        new TypeReference<MessageWrapper<KnowledgeDocumentSplitEvent>>() {
                        });
                splitExecutionService.markSplitFailed(
                        wrapper.getBody().getDocumentId(),
                        wrapper.getBody().getChunkLogId(),
                        ex.getMessage());
            } catch (Exception ignored) {
                log.error("标记文档切片失败时再次异常", ignored);
            }
            throw new RuntimeException(ex);
        }
    }
}
