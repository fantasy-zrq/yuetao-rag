package com.rag.cn.yuetaoragbackend.framework.web;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.json.JSONUtil;
import com.rag.cn.yuetaoragbackend.config.properties.AuthProperties;
import com.rag.cn.yuetaoragbackend.framework.context.LoginUser;
import com.rag.cn.yuetaoragbackend.framework.context.UserContext;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

@RequiredArgsConstructor
public class UserContextInterceptor implements HandlerInterceptor {

    private final AuthProperties authProperties;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (request.getDispatcherType() != DispatcherType.REQUEST) {
            return true;
        }
        String tokenName = SaManager.getConfig().getTokenName();
        if (!StringUtils.hasText(request.getHeader(tokenName))) {
            return true;
        }
        // checkLogin/getLoginIdAsString 会触发 Sa-Token 的 active-timeout 校验与自动续期。
        StpUtil.checkLogin();
        String loginId = StpUtil.getLoginIdAsString();
        Object sessionValue = StpUtil.getTokenSession().get(authProperties.getTokenSessionLoginUserKey());
        if (sessionValue instanceof String raw && StringUtils.hasText(raw)) {
            UserContext.set(JSONUtil.toBean(raw, LoginUser.class));
            return true;
        }
        if (sessionValue instanceof LoginUser loginUser) {
            UserContext.set(loginUser);
            return true;
        }
        UserContext.set(LoginUser.builder().userId(loginId).build());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }
}
