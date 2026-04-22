package com.rag.cn.yuetaoragbackend.scaffold;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * @author zrq
 * 2026/04/22 14:30
 */
class PersistenceScaffoldPresenceTests {

    @Test
    void shouldContainCorePersistenceAndApiScaffoldClasses() throws Exception {
        List<String> classNames = List.of(
            "com.rag.cn.yuetaoragbackend.dao.entity.BaseDO",
            "com.rag.cn.yuetaoragbackend.dao.entity.KnowledgeBaseDO",
            "com.rag.cn.yuetaoragbackend.dao.entity.KnowledgeDocumentDO",
            "com.rag.cn.yuetaoragbackend.dao.entity.ChunkDO",
            "com.rag.cn.yuetaoragbackend.dao.entity.ChunkVectorDO",
            "com.rag.cn.yuetaoragbackend.dao.entity.ChatSessionDO",
            "com.rag.cn.yuetaoragbackend.dao.entity.ChatMessageDO",
            "com.rag.cn.yuetaoragbackend.dao.mapper.KnowledgeBaseMapper",
            "com.rag.cn.yuetaoragbackend.dao.mapper.KnowledgeDocumentMapper",
            "com.rag.cn.yuetaoragbackend.dao.mapper.ChunkMapper",
            "com.rag.cn.yuetaoragbackend.dao.mapper.ChunkVectorMapper",
            "com.rag.cn.yuetaoragbackend.dao.mapper.ChatSessionMapper",
            "com.rag.cn.yuetaoragbackend.dao.mapper.ChatMessageMapper",
            "com.rag.cn.yuetaoragbackend.service.KnowledgeBaseService",
            "com.rag.cn.yuetaoragbackend.service.KnowledgeDocumentService",
            "com.rag.cn.yuetaoragbackend.service.ChatSessionService",
            "com.rag.cn.yuetaoragbackend.service.ChatMessageService",
            "com.rag.cn.yuetaoragbackend.service.impl.KnowledgeBaseServiceImpl",
            "com.rag.cn.yuetaoragbackend.service.impl.KnowledgeDocumentServiceImpl",
            "com.rag.cn.yuetaoragbackend.service.impl.ChatSessionServiceImpl",
            "com.rag.cn.yuetaoragbackend.service.impl.ChatMessageServiceImpl",
            "com.rag.cn.yuetaoragbackend.controller.KnowledgeBaseController",
            "com.rag.cn.yuetaoragbackend.controller.KnowledgeDocumentController",
            "com.rag.cn.yuetaoragbackend.controller.ChatSessionController",
            "com.rag.cn.yuetaoragbackend.controller.ChatMessageController"
        );

        for (String className : classNames) {
            assertThat(Class.forName(className)).as("missing class %s", className).isNotNull();
        }
    }
}
