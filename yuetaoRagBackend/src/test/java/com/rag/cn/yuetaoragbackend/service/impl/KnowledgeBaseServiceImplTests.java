package com.rag.cn.yuetaoragbackend.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.rag.cn.yuetaoragbackend.config.enums.CommonStatusEnum;
import com.rag.cn.yuetaoragbackend.config.enums.DeleteFlagEnum;
import com.rag.cn.yuetaoragbackend.dao.entity.KnowledgeBaseDO;
import com.rag.cn.yuetaoragbackend.dao.mapper.KnowledgeBaseMapper;
import com.rag.cn.yuetaoragbackend.dao.mapper.KnowledgeDocumentMapper;
import com.rag.cn.yuetaoragbackend.dto.req.CreateKnowledgeBaseReq;
import com.rag.cn.yuetaoragbackend.dto.req.DeleteKnowledgeBaseReq;
import com.rag.cn.yuetaoragbackend.dto.req.UpdateKnowledgeBaseReq;
import com.rag.cn.yuetaoragbackend.dto.resp.KnowledgeBaseCreateResp;
import com.rag.cn.yuetaoragbackend.dto.resp.KnowledgeBaseDetailResp;
import com.rag.cn.yuetaoragbackend.framework.context.LoginUser;
import com.rag.cn.yuetaoragbackend.framework.context.UserContext;
import com.rag.cn.yuetaoragbackend.framework.exception.ClientException;
import com.rag.cn.yuetaoragbackend.service.file.FileService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * @author zrq
 * 2026/04/26 10:00
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeBaseServiceImplTests {

    @Mock
    private KnowledgeBaseMapper knowledgeBaseMapper;

    @Mock
    private KnowledgeDocumentMapper knowledgeDocumentMapper;

    @Mock
    private FileService fileService;

    @InjectMocks
    private KnowledgeBaseServiceImpl knowledgeBaseService;

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void shouldCreateKnowledgeBaseWhenNameAndCollectionNameAreAvailable() {
        UserContext.set(LoginUser.builder().userId("10001").build());
        CreateKnowledgeBaseReq request = new CreateKnowledgeBaseReq()
                .setName("知识库A")
                .setDescription("desc")
                .setEmbeddingModel("text-embedding-v4")
                .setCollectionName("rag-kb-01");
        when(knowledgeBaseMapper.selectCount(any(Wrapper.class))).thenReturn(0L, 0L);
        when(knowledgeBaseMapper.insert(any(KnowledgeBaseDO.class))).thenAnswer(invocation -> {
            KnowledgeBaseDO argument = invocation.getArgument(0);
            argument.setId(123L);
            argument.setDeleteFlag(DeleteFlagEnum.NORMAL.getCode());
            return 1;
        });

        KnowledgeBaseCreateResp response = knowledgeBaseService.createKnowledgeBase(request);

        assertThat(response.getId()).isEqualTo(123L);
        assertThat(response.getName()).isEqualTo("知识库A");
        assertThat(response.getCollectionName()).isEqualTo("rag-kb-01");
        assertThat(response.getStatus()).isEqualTo(CommonStatusEnum.ENABLED.getCode());
        ArgumentCaptor<KnowledgeBaseDO> captor = ArgumentCaptor.forClass(KnowledgeBaseDO.class);
        verify(knowledgeBaseMapper).insert(captor.capture());
        assertThat(captor.getValue().getCreatedBy()).isEqualTo(10001L);
        assertThat(captor.getValue().getUpdatedBy()).isEqualTo(10001L);
        verify(fileService).createBucketIfAbsent("rag-kb-01");
    }

    @Test
    void shouldRejectCreateWhenCollectionNameIsInvalid() {
        UserContext.set(LoginUser.builder().userId("10001").build());
        CreateKnowledgeBaseReq request = new CreateKnowledgeBaseReq()
                .setName("知识库A")
                .setEmbeddingModel("text-embedding-v4")
                .setCollectionName("RAG-KB-01");

        assertThatThrownBy(() -> knowledgeBaseService.createKnowledgeBase(request))
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("collectionName");

        verify(knowledgeBaseMapper, never()).selectCount(any(Wrapper.class));
        verify(fileService, never()).createBucketIfAbsent(any(String.class));
    }

    @Test
    void shouldRejectCreateWhenNameAlreadyExists() {
        UserContext.set(LoginUser.builder().userId("10001").build());
        CreateKnowledgeBaseReq request = new CreateKnowledgeBaseReq()
                .setName("知识库A")
                .setEmbeddingModel("text-embedding-v4")
                .setCollectionName("rag-kb-01");
        when(knowledgeBaseMapper.selectCount(any(Wrapper.class))).thenReturn(1L);

        assertThatThrownBy(() -> knowledgeBaseService.createKnowledgeBase(request))
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("知识库名称");

        verify(fileService, never()).createBucketIfAbsent(any(String.class));
    }

    @Test
    void shouldRejectCreateWhenCollectionNameAlreadyExists() {
        UserContext.set(LoginUser.builder().userId("10001").build());
        CreateKnowledgeBaseReq request = new CreateKnowledgeBaseReq()
                .setName("知识库A")
                .setEmbeddingModel("text-embedding-v4")
                .setCollectionName("rag-kb-01");
        when(knowledgeBaseMapper.selectCount(any(Wrapper.class))).thenReturn(0L, 1L);

        assertThatThrownBy(() -> knowledgeBaseService.createKnowledgeBase(request))
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("collectionName");

        verify(fileService, never()).createBucketIfAbsent(any(String.class));
    }

    @Test
    void shouldUpdateKnowledgeBaseNameOnly() {
        UserContext.set(LoginUser.builder().userId("10001").build());
        KnowledgeBaseDO existing = new KnowledgeBaseDO();
        existing.setId(100L);
        existing.setName("旧名称");
        existing.setCollectionName("rag-kb-01");
        existing.setEmbeddingModel("text-embedding-v4");
        existing.setDeleteFlag(DeleteFlagEnum.NORMAL.getCode());
        UpdateKnowledgeBaseReq request = new UpdateKnowledgeBaseReq()
                .setId(100L)
                .setName("新名称");
        when(knowledgeBaseMapper.selectOne(any(Wrapper.class))).thenReturn(existing);
        when(knowledgeBaseMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(knowledgeBaseMapper.updateById(any(KnowledgeBaseDO.class))).thenReturn(1);

        KnowledgeBaseDetailResp response = knowledgeBaseService.updateKnowledgeBase(request);

        assertThat(response.getId()).isEqualTo(100L);
        assertThat(response.getName()).isEqualTo("新名称");
        assertThat(response.getCollectionName()).isEqualTo("rag-kb-01");
        ArgumentCaptor<KnowledgeBaseDO> captor = ArgumentCaptor.forClass(KnowledgeBaseDO.class);
        verify(knowledgeBaseMapper).updateById(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(100L);
        assertThat(captor.getValue().getName()).isEqualTo("新名称");
        assertThat(captor.getValue().getUpdatedBy()).isEqualTo(10001L);
    }

    @Test
    void shouldDeleteKnowledgeBaseLogicallyWhenNoDocumentsRemain() {
        UserContext.set(LoginUser.builder().userId("10001").build());
        KnowledgeBaseDO existing = new KnowledgeBaseDO();
        existing.setId(100L);
        existing.setName("知识库A");
        existing.setCollectionName("rag-kb-01");
        existing.setDeleteFlag(DeleteFlagEnum.NORMAL.getCode());
        DeleteKnowledgeBaseReq request = new DeleteKnowledgeBaseReq().setId(100L);
        when(knowledgeBaseMapper.selectOne(any(Wrapper.class))).thenReturn(existing);
        when(knowledgeDocumentMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(knowledgeBaseMapper.updateById(any(KnowledgeBaseDO.class))).thenReturn(1);

        knowledgeBaseService.deleteKnowledgeBase(request);

        verify(fileService).deleteBucket("rag-kb-01");
        ArgumentCaptor<KnowledgeBaseDO> captor = ArgumentCaptor.forClass(KnowledgeBaseDO.class);
        verify(knowledgeBaseMapper).updateById(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(100L);
        assertThat(captor.getValue().getDeleteFlag()).isEqualTo(DeleteFlagEnum.DELETED.getCode());
        assertThat(captor.getValue().getUpdatedBy()).isEqualTo(10001L);
    }

    @Test
    void shouldRejectDeleteWhenKnowledgeBaseStillHasDocuments() {
        UserContext.set(LoginUser.builder().userId("10001").build());
        KnowledgeBaseDO existing = new KnowledgeBaseDO();
        existing.setId(100L);
        existing.setName("知识库A");
        existing.setCollectionName("rag-kb-01");
        existing.setDeleteFlag(DeleteFlagEnum.NORMAL.getCode());
        DeleteKnowledgeBaseReq request = new DeleteKnowledgeBaseReq().setId(100L);
        when(knowledgeBaseMapper.selectOne(any(Wrapper.class))).thenReturn(existing);
        when(knowledgeDocumentMapper.selectCount(any(Wrapper.class))).thenReturn(1L);

        assertThatThrownBy(() -> knowledgeBaseService.deleteKnowledgeBase(request))
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("仍有文档");

        verify(fileService, never()).deleteBucket(any(String.class));
        verify(knowledgeBaseMapper, never()).updateById(any(KnowledgeBaseDO.class));
    }
}
