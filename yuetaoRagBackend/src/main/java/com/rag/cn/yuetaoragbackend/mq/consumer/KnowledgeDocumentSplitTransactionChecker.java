package com.rag.cn.yuetaoragbackend.mq.consumer;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.rag.cn.yuetaoragbackend.config.enums.DeleteFlagEnum;
import com.rag.cn.yuetaoragbackend.config.enums.ParseStatusEnum;
import com.rag.cn.yuetaoragbackend.dao.entity.KnowledgeDocumentDO;
import com.rag.cn.yuetaoragbackend.dao.mapper.KnowledgeDocumentMapper;
import com.rag.cn.yuetaoragbackend.mq.MessageWrapper;
import com.rag.cn.yuetaoragbackend.mq.event.KnowledgeDocumentSplitEvent;
import com.rag.cn.yuetaoragbackend.mq.producer.DelegatingTransactionListener;
import com.rag.cn.yuetaoragbackend.mq.producer.TransactionChecker;
import com.rag.cn.yuetaoragbackend.service.impl.KnowledgeDocumentSplitServiceImpl;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * @author zrq
 * 2026/04/27 10:20
 */
@Component
@RequiredArgsConstructor
public class KnowledgeDocumentSplitTransactionChecker implements TransactionChecker {

    private final DelegatingTransactionListener transactionListener;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;

    @PostConstruct
    public void registerChecker() {
        transactionListener.registerChecker(KnowledgeDocumentSplitServiceImpl.SPLIT_TOPIC, this);
    }

    @Override
    public boolean check(MessageWrapper<?> message) {
        KnowledgeDocumentSplitEvent event = (KnowledgeDocumentSplitEvent) message.getBody();
        KnowledgeDocumentDO documentDO = knowledgeDocumentMapper.selectOne(Wrappers.<KnowledgeDocumentDO>lambdaQuery()
                .eq(KnowledgeDocumentDO::getId, event.getDocumentId())
                .eq(KnowledgeDocumentDO::getDeleteFlag, DeleteFlagEnum.NORMAL.getCode()));
        if (documentDO == null) {
            return false;
        }
        return ParseStatusEnum.PROCESSING.getCode().equals(documentDO.getParseStatus())
                || ParseStatusEnum.SUCCESS.getCode().equals(documentDO.getParseStatus())
                || ParseStatusEnum.FAILED.getCode().equals(documentDO.getParseStatus());
    }
}
