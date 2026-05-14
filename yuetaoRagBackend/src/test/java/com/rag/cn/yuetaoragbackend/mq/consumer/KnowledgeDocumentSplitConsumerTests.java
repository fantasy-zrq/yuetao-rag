package com.rag.cn.yuetaoragbackend.mq.consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.alibaba.fastjson2.JSON;
import com.rag.cn.yuetaoragbackend.framework.idempotent.IdempotentConsumeAspect;
import com.rag.cn.yuetaoragbackend.mq.MessageWrapper;
import com.rag.cn.yuetaoragbackend.mq.event.KnowledgeDocumentSplitEvent;
import com.rag.cn.yuetaoragbackend.service.impl.KnowledgeDocumentSplitServiceImpl;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig
@ContextConfiguration(classes = KnowledgeDocumentSplitConsumerTests.TestConfiguration.class)
class KnowledgeDocumentSplitConsumerTests {

    @jakarta.annotation.Resource
    private KnowledgeDocumentSplitConsumer knowledgeDocumentSplitConsumer;

    @jakarta.annotation.Resource
    private KnowledgeDocumentSplitServiceImpl splitExecutionService;

    @Test
    void shouldSkipDuplicateSplitMessageWhenRocketMqRedeliversSameChunkLog() {
        MessageExt messageExt = buildSplitMessage(200L, 900L, "split-message-1");

        knowledgeDocumentSplitConsumer.onMessage(messageExt);
        knowledgeDocumentSplitConsumer.onMessage(messageExt);

        verify(splitExecutionService).processSplit(200L, 900L);
        verifyNoMoreInteractions(splitExecutionService);
    }

    private MessageExt buildSplitMessage(Long documentId, Long chunkLogId, String uuid) {
        MessageWrapper<KnowledgeDocumentSplitEvent> wrapper = MessageWrapper.<KnowledgeDocumentSplitEvent>builder()
                .keys(String.valueOf(documentId))
                .uuid(uuid)
                .timestamp(1L)
                .body(new KnowledgeDocumentSplitEvent(documentId, chunkLogId))
                .build();
        MessageExt messageExt = new MessageExt();
        messageExt.setBody(JSON.toJSONBytes(wrapper));
        messageExt.setKeys(String.valueOf(documentId));
        return messageExt;
    }

    @Configuration
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    static class TestConfiguration {

        @Bean
        KnowledgeDocumentSplitConsumer knowledgeDocumentSplitConsumer(KnowledgeDocumentSplitServiceImpl splitExecutionService) {
            return new KnowledgeDocumentSplitConsumer(splitExecutionService);
        }

        @Bean
        KnowledgeDocumentSplitServiceImpl splitExecutionService() {
            return mock(KnowledgeDocumentSplitServiceImpl.class);
        }

        @Bean
        IdempotentConsumeAspect idempotentConsumeAspect(StringRedisTemplate stringRedisTemplate) {
            return new IdempotentConsumeAspect(stringRedisTemplate);
        }

        @Bean
        StringRedisTemplate stringRedisTemplate() {
            StringRedisTemplate stringRedisTemplate = mock(StringRedisTemplate.class);
            @SuppressWarnings("unchecked")
            ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
            ConcurrentHashMap<String, String> consumedState = new ConcurrentHashMap<>();

            when(stringRedisTemplate.execute(any(RedisScript.class), anyList(), anyString(), anyString()))
                    .thenAnswer(invocation -> {
                        List<String> keys = invocation.getArgument(1);
                        String key = keys.get(0);
                        String value = invocation.getArgument(2);
                        return consumedState.putIfAbsent(key, value);
                    });
            when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
            doAnswer(invocation -> {
                consumedState.put(invocation.getArgument(0), invocation.getArgument(1));
                return null;
            }).when(valueOperations).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
            doAnswer(invocation -> consumedState.remove(invocation.getArgument(0)) != null)
                    .when(stringRedisTemplate).delete(anyString());
            return stringRedisTemplate;
        }
    }
}
