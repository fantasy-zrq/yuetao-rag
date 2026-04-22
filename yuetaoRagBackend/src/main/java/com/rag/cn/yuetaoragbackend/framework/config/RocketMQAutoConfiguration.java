package com.rag.cn.yuetaoragbackend.framework.config;

import com.rag.cn.yuetaoragbackend.framework.mq.producer.DelegatingTransactionListener;
import com.rag.cn.yuetaoragbackend.framework.mq.producer.MessageQueueProducer;
import com.rag.cn.yuetaoragbackend.framework.mq.producer.RocketMQProducerAdapter;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RocketMQ 消息队列自动装配配置
 */
@Configuration
public class RocketMQAutoConfiguration {

    @Bean
    public DelegatingTransactionListener delegatingTransactionListener() {
        return new DelegatingTransactionListener();
    }

    @Bean
    public MessageQueueProducer messageQueueProducer(RocketMQTemplate rocketMQTemplate,
                                                     DelegatingTransactionListener transactionListener) {
        return new RocketMQProducerAdapter(rocketMQTemplate, transactionListener);
    }
}
