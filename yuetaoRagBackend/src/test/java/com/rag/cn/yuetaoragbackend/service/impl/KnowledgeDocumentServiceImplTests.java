package com.rag.cn.yuetaoragbackend.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.rag.cn.yuetaoragbackend.config.enums.CommonStatusEnum;
import com.rag.cn.yuetaoragbackend.config.enums.ParseStatusEnum;
import com.rag.cn.yuetaoragbackend.dao.entity.KnowledgeBaseDO;
import com.rag.cn.yuetaoragbackend.dao.entity.KnowledgeDocumentDO;
import com.rag.cn.yuetaoragbackend.dao.entity.DocumentDepartmentAuthDO;
import com.rag.cn.yuetaoragbackend.dao.mapper.DocumentDepartmentAuthMapper;
import com.rag.cn.yuetaoragbackend.dao.mapper.KnowledgeBaseMapper;
import com.rag.cn.yuetaoragbackend.dao.mapper.KnowledgeDocumentMapper;
import com.rag.cn.yuetaoragbackend.dto.req.CreateKnowledgeDocumentReq;
import com.rag.cn.yuetaoragbackend.dto.resp.KnowledgeDocumentCreateResp;
import com.rag.cn.yuetaoragbackend.framework.context.LoginUser;
import com.rag.cn.yuetaoragbackend.framework.context.UserContext;
import com.rag.cn.yuetaoragbackend.framework.exception.ClientException;
import com.rag.cn.yuetaoragbackend.mq.producer.MessageQueueProducer;
import com.rag.cn.yuetaoragbackend.service.file.FileService;
import com.rag.cn.yuetaoragbackend.service.file.UploadObjectResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;

/**
 * @author zrq
 * 2026/04/26 16:20
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeDocumentServiceImplTests {

    @Mock
    private KnowledgeDocumentMapper knowledgeDocumentMapper;

    @Mock
    private KnowledgeBaseMapper knowledgeBaseMapper;

    @Mock
    private DocumentDepartmentAuthMapper documentDepartmentAuthMapper;

    @Mock
    private FileService fileService;

    @Mock
    private MessageQueueProducer messageQueueProducer;

    @InjectMocks
    private KnowledgeDocumentServiceImpl knowledgeDocumentService;

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void shouldUploadDocumentAndPersistMetadata() throws Exception {
        UserContext.set(LoginUser.builder().userId("10001").build());
        KnowledgeBaseDO knowledgeBaseDO = new KnowledgeBaseDO();
        knowledgeBaseDO.setId(100L);
        knowledgeBaseDO.setCollectionName("kb-upload-01");
        knowledgeBaseDO.setEmbeddingModel("text-embedding-v4");
        CreateKnowledgeDocumentReq request = new CreateKnowledgeDocumentReq()
                .setKnowledgeBaseId(100L)
                .setChunkMode("FIXED")
                .setChunkConfig("{\"chunkSize\":512,\"chunkOverlap\":50}")
                .setVisibilityScope("INTERNAL")
                .setMinRankLevel(10);
        MockMultipartFile file = new MockMultipartFile("file", "guide.pdf", "application/pdf", "hello".getBytes());
        when(knowledgeBaseMapper.selectOne(any(Wrapper.class))).thenReturn(knowledgeBaseDO);
        when(fileService.uploadObject(eq("kb-upload-01"), any(String.class), eq(file.getBytes()), eq("application/pdf")))
                .thenReturn(new UploadObjectResult("etag-001", "http://rustfs/kb-upload-01/guide.pdf"));

        KnowledgeDocumentCreateResp response = knowledgeDocumentService.createKnowledgeDocument(file, request);

        assertThat(response.getKnowledgeBaseId()).isEqualTo(100L);
        assertThat(response.getTitle()).isEqualTo("guide.pdf");
        assertThat(response.getParseStatus()).isEqualTo(ParseStatusEnum.PENDING.getCode());
        assertThat(response.getStatus()).isEqualTo(CommonStatusEnum.ENABLED.getCode());
        assertThat(response.getChunkMode()).isEqualTo("FIXED");
        ArgumentCaptor<KnowledgeDocumentDO> captor = ArgumentCaptor.forClass(KnowledgeDocumentDO.class);
        verify(knowledgeDocumentMapper).insert(captor.capture());
        KnowledgeDocumentDO inserted = captor.getValue();
        assertThat(inserted.getKnowledgeBaseId()).isEqualTo(100L);
        assertThat(inserted.getTitle()).isEqualTo("guide.pdf");
        assertThat(inserted.getSourceType()).isEqualTo("UPLOAD");
        assertThat(inserted.getMimeType()).isEqualTo("application/pdf");
        assertThat(inserted.getStorageBucket()).isEqualTo("kb-upload-01");
        assertThat(inserted.getStorageKey()).endsWith("_guide.pdf");
        assertThat(inserted.getStorageEtag()).isEqualTo("etag-001");
        assertThat(inserted.getStorageUrl()).isEqualTo("http://rustfs/kb-upload-01/guide.pdf");
        assertThat(inserted.getFileSize()).isEqualTo(5L);
        assertThat(inserted.getChunkMode()).isEqualTo("FIXED");
        assertThat(inserted.getChunkConfig()).isEqualTo("{\"chunkSize\":512,\"chunkOverlap\":50}");
        assertThat(inserted.getCreatedBy()).isEqualTo(10001L);
        assertThat(inserted.getUpdatedBy()).isEqualTo(10001L);
    }

    @Test
    void shouldRejectUploadWhenChunkModeMissing() {
        UserContext.set(LoginUser.builder().userId("10001").build());
        CreateKnowledgeDocumentReq request = new CreateKnowledgeDocumentReq()
                .setKnowledgeBaseId(100L)
                .setChunkConfig("{\"chunkSize\":512,\"chunkOverlap\":50}");
        MockMultipartFile file = new MockMultipartFile("file", "guide.pdf", "application/pdf", "hello".getBytes());

        assertThatThrownBy(() -> knowledgeDocumentService.createKnowledgeDocument(file, request))
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("chunkMode");
    }

    @Test
    void shouldRejectSensitiveDocumentWithoutDepartmentAuth() {
        UserContext.set(LoginUser.builder().userId("10001").build());
        CreateKnowledgeDocumentReq request = new CreateKnowledgeDocumentReq()
                .setKnowledgeBaseId(100L)
                .setChunkMode("FIXED")
                .setChunkConfig("{\"chunkSize\":512,\"chunkOverlap\":50}")
                .setVisibilityScope("SENSITIVE")
                .setMinRankLevel(10);
        MockMultipartFile file = new MockMultipartFile("file", "guide.pdf", "application/pdf", "hello".getBytes());

        assertThatThrownBy(() -> knowledgeDocumentService.createKnowledgeDocument(file, request))
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("授权部门");
    }

    @Test
    void shouldRejectBlankSensitiveDocumentWithoutDepartmentAuthAfterTrimmingVisibilityScope() {
        UserContext.set(LoginUser.builder().userId("10001").build());
        CreateKnowledgeDocumentReq request = new CreateKnowledgeDocumentReq()
                .setKnowledgeBaseId(100L)
                .setChunkMode("FIXED")
                .setChunkConfig("{\"chunkSize\":512,\"chunkOverlap\":50}")
                .setVisibilityScope(" SENSITIVE ")
                .setMinRankLevel(10);
        MockMultipartFile file = new MockMultipartFile("file", "guide.pdf", "application/pdf", "hello".getBytes());

        assertThatThrownBy(() -> knowledgeDocumentService.createKnowledgeDocument(file, request))
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("授权部门");
    }

    @Test
    void shouldRejectUnknownVisibilityScopeWhenCreatingDocument() {
        UserContext.set(LoginUser.builder().userId("10001").build());
        CreateKnowledgeDocumentReq request = new CreateKnowledgeDocumentReq()
                .setKnowledgeBaseId(100L)
                .setChunkMode("FIXED")
                .setChunkConfig("{\"chunkSize\":512,\"chunkOverlap\":50}")
                .setVisibilityScope("PUBLIC")
                .setMinRankLevel(10);
        MockMultipartFile file = new MockMultipartFile("file", "guide.pdf", "application/pdf", "hello".getBytes());

        assertThatThrownBy(() -> knowledgeDocumentService.createKnowledgeDocument(file, request))
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("visibilityScope");
    }

    @Test
    void shouldPersistSensitiveDocumentDepartmentAuth() throws Exception {
        UserContext.set(LoginUser.builder().userId("10001").build());
        KnowledgeBaseDO knowledgeBaseDO = new KnowledgeBaseDO();
        knowledgeBaseDO.setId(100L);
        knowledgeBaseDO.setCollectionName("kb-sensitive-01");
        knowledgeBaseDO.setEmbeddingModel("text-embedding-v4");
        CreateKnowledgeDocumentReq request = new CreateKnowledgeDocumentReq()
                .setKnowledgeBaseId(100L)
                .setChunkMode("FIXED")
                .setChunkConfig("{\"chunkSize\":512,\"chunkOverlap\":50}")
                .setVisibilityScope("SENSITIVE")
                .setMinRankLevel(10)
                .setAuthorizedDepartmentIds(java.util.Arrays.asList(11L, null, 11L, 12L));
        MockMultipartFile file = new MockMultipartFile("file", "secret.pdf", "application/pdf", "hello".getBytes());
        when(knowledgeBaseMapper.selectOne(any(Wrapper.class))).thenReturn(knowledgeBaseDO);
        when(fileService.uploadObject(eq("kb-sensitive-01"), any(String.class), eq(file.getBytes()), eq("application/pdf")))
                .thenReturn(new UploadObjectResult("etag-sensitive-001", "http://rustfs/kb-sensitive-01/secret.pdf"));

        KnowledgeDocumentCreateResp response = knowledgeDocumentService.createKnowledgeDocument(file, request);

        assertThat(response.getAuthorizedDepartmentIds()).containsExactly(11L, 12L);
        ArgumentCaptor<KnowledgeDocumentDO> documentCaptor = ArgumentCaptor.forClass(KnowledgeDocumentDO.class);
        verify(knowledgeDocumentMapper).insert(documentCaptor.capture());
        Long documentId = documentCaptor.getValue().getId();
        ArgumentCaptor<DocumentDepartmentAuthDO> authCaptor = ArgumentCaptor.forClass(DocumentDepartmentAuthDO.class);
        verify(documentDepartmentAuthMapper, org.mockito.Mockito.times(2)).insert(authCaptor.capture());
        assertThat(authCaptor.getAllValues())
                .extracting(DocumentDepartmentAuthDO::getDocumentId, DocumentDepartmentAuthDO::getDepartmentId)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(documentId, 11L),
                        org.assertj.core.groups.Tuple.tuple(documentId, 12L));
    }
}
