package com.rag.cn.yuetaoragbackend.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rag.cn.yuetaoragbackend.config.enums.ChatSessionStatusEnum;
import com.rag.cn.yuetaoragbackend.config.enums.DeleteFlagEnum;
import com.rag.cn.yuetaoragbackend.dao.entity.ChatSessionDO;
import com.rag.cn.yuetaoragbackend.dao.mapper.ChatSessionMapper;
import com.rag.cn.yuetaoragbackend.dto.req.CreateChatSessionReq;
import com.rag.cn.yuetaoragbackend.dto.resp.ChatSessionCreateResp;
import com.rag.cn.yuetaoragbackend.dto.resp.ChatSessionDetailResp;
import com.rag.cn.yuetaoragbackend.framework.context.LoginUser;
import com.rag.cn.yuetaoragbackend.framework.context.UserContext;
import com.rag.cn.yuetaoragbackend.framework.exception.ClientException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChatSessionServiceImplTests {

    @Mock
    private ChatSessionMapper chatSessionMapper;

    @InjectMocks
    private ChatSessionServiceImpl chatSessionService;

    @BeforeEach
    void setUp() {
        UserContext.set(LoginUser.builder().userId("20").build());
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void shouldReturnSessionWhenOwnerMatches() {
        ChatSessionDO session = new ChatSessionDO();
        session.setId(1L);
        session.setUserId(20L);
        session.setTitle("test");
        session.setDeleteFlag(DeleteFlagEnum.NORMAL.getCode());
        when(chatSessionMapper.selectOne(any())).thenReturn(session);

        ChatSessionDetailResp result = chatSessionService.getChatSession(1L);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getUserId()).isEqualTo(20L);
    }

    @Test
    void shouldThrowWhenSessionBelongsToAnotherUser() {
        ChatSessionDO session = new ChatSessionDO();
        session.setId(2L);
        session.setUserId(99L);
        session.setTitle("other");
        session.setDeleteFlag(DeleteFlagEnum.NORMAL.getCode());
        when(chatSessionMapper.selectOne(any())).thenReturn(session);

        assertThatThrownBy(() -> chatSessionService.getChatSession(2L))
                .isInstanceOf(ClientException.class)
                .hasMessage("无权访问该会话");
    }

    @Test
    void shouldReturnNullWhenSessionNotFound() {
        when(chatSessionMapper.selectOne(any())).thenReturn(null);

        ChatSessionDetailResp result = chatSessionService.getChatSession(999L);

        assertThat(result).isNull();
    }

    @Test
    void shouldReturnNullWhenSessionDeleted() {
        ChatSessionDetailResp result = chatSessionService.getChatSession(3L);

        assertThat(result).isNull();
    }

    @Test
    void shouldRejectDeleteWhenSessionBelongsToAnotherUser() {
        ChatSessionDO session = new ChatSessionDO();
        session.setId(4L);
        session.setUserId(99L);
        session.setDeleteFlag(DeleteFlagEnum.NORMAL.getCode());
        lenient().when(chatSessionMapper.selectOne(any())).thenReturn(session);

        assertThatThrownBy(() -> chatSessionService.deleteChatSession(4L))
                .isInstanceOf(ClientException.class)
                .hasMessage("无权访问该会话");
        verify(chatSessionMapper, never()).updateById(any(ChatSessionDO.class));
    }

    @Test
    void shouldCreateSessionForCurrentUser() {
        ChatSessionCreateResp result = chatSessionService.createChatSession(new CreateChatSessionReq()
                .setTitle("new-session")
                .setStatus(ChatSessionStatusEnum.ACTIVE.getCode()));

        ArgumentCaptor<ChatSessionDO> sessionCaptor = ArgumentCaptor.forClass(ChatSessionDO.class);
        verify(chatSessionMapper).insert(sessionCaptor.capture());
        assertThat(sessionCaptor.getValue().getUserId()).isEqualTo(20L);
        assertThat(sessionCaptor.getValue().getTitle()).isEqualTo("new-session");
        assertThat(result.getUserId()).isEqualTo(20L);
    }
}
