package com.rag.cn.yuetaoragbackend.framework.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.rag.cn.yuetaoragbackend.config.properties.AuthProperties;
import com.rag.cn.yuetaoragbackend.framework.context.LoginUser;
import com.rag.cn.yuetaoragbackend.framework.context.UserContext;
import jakarta.servlet.DispatcherType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class UserContextInterceptorTests {

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void shouldClearStaleUserContextWhenRequestHasNoToken() {
        UserContext.set(LoginUser.builder().userId("10001").build());
        UserContextInterceptor interceptor = new UserContextInterceptor(new AuthProperties());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setDispatcherType(DispatcherType.REQUEST);

        boolean allowed = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        assertThat(allowed).isTrue();
        assertThat(UserContext.hasUser()).isFalse();
    }

    @Test
    void shouldKeepExplicitlyBoundUserContextWhenRequestMarksItAsCurrent() {
        UserContext.set(LoginUser.builder().userId("10001").build());
        UserContextInterceptor interceptor = new UserContextInterceptor(new AuthProperties());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setDispatcherType(DispatcherType.REQUEST);
        request.setAttribute(UserContextInterceptor.USER_CONTEXT_BOUND_ATTRIBUTE, Boolean.TRUE);

        boolean allowed = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        assertThat(allowed).isTrue();
        assertThat(UserContext.hasUser()).isTrue();
        assertThat(UserContext.getUserId()).isEqualTo("10001");
    }
}
