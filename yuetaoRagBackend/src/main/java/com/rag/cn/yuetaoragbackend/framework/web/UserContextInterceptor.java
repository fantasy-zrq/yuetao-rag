package com.rag.cn.yuetaoragbackend.framework.web;

import cn.dev33.satoken.stp.StpUtil;
import com.rag.cn.yuetaoragbackend.framework.context.LoginUser;
import com.rag.cn.yuetaoragbackend.framework.context.UserContext;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

public class UserContextInterceptor implements HandlerInterceptor {

    private static final String LOGIN_USER_SESSION_KEY = "loginUser";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (request.getDispatcherType() != DispatcherType.REQUEST) {
            return true;
        }
        if (StpUtil.isLogin()) {
            Object value = StpUtil.getSession().get(LOGIN_USER_SESSION_KEY);
            if (value instanceof LoginUser loginUser) {
                UserContext.set(loginUser);
            } else {
                UserContext.set(LoginUser.builder()
                        .userId(String.valueOf(StpUtil.getLoginId()))
                        .build());
            }
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }
}
