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

    /**
     * 上游鉴权组件如果已在进入拦截器前显式绑定当前请求的 UserContext，
     * 可以设置该属性，避免这里把“当前请求的有效上下文”误当成残留脏数据清掉。
     */
    public static final String USER_CONTEXT_BOUND_ATTRIBUTE =
            UserContextInterceptor.class.getName() + ".USER_CONTEXT_BOUND";

    private final AuthProperties authProperties;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (request.getDispatcherType() != DispatcherType.REQUEST) {
            return true;
        }
        // 默认清理线程复用残留的登录态；只有上游明确声明“当前请求已绑定用户上下文”时才保留。
        if (!Boolean.TRUE.equals(request.getAttribute(USER_CONTEXT_BOUND_ATTRIBUTE))) {
            UserContext.clear();
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
