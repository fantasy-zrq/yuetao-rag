package com.rag.cn.yuetaoragbackend.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.rag.cn.yuetaoragbackend.config.enums.DeleteFlagEnum;
import com.rag.cn.yuetaoragbackend.dao.entity.ChatSessionDO;
import com.rag.cn.yuetaoragbackend.dao.mapper.ChatSessionMapper;
import com.rag.cn.yuetaoragbackend.dto.resp.ChatSessionDetailResp;
import com.rag.cn.yuetaoragbackend.framework.context.LoginUser;
import com.rag.cn.yuetaoragbackend.framework.context.UserContext;
import com.rag.cn.yuetaoragbackend.framework.exception.ClientException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
        when(chatSessionMapper.selectById(1L)).thenReturn(session);

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
        when(chatSessionMapper.selectById(2L)).thenReturn(session);

        assertThatThrownBy(() -> chatSessionService.getChatSession(2L))
                .isInstanceOf(ClientException.class)
                .hasMessage("无权访问该会话");
    }

    @Test
    void shouldReturnNullWhenSessionNotFound() {
        when(chatSessionMapper.selectById(999L)).thenReturn(null);

        ChatSessionDetailResp result = chatSessionService.getChatSession(999L);

        assertThat(result).isNull();
    }
}
