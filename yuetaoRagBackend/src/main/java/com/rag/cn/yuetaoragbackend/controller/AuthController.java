package com.rag.cn.yuetaoragbackend.controller;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.rag.cn.yuetaoragbackend.config.enums.CommonStatusEnum;
import com.rag.cn.yuetaoragbackend.config.enums.DeleteFlagEnum;
import com.rag.cn.yuetaoragbackend.config.properties.AuthProperties;
import com.rag.cn.yuetaoragbackend.dao.entity.UserDO;
import com.rag.cn.yuetaoragbackend.dao.mapper.UserMapper;
import com.rag.cn.yuetaoragbackend.dto.req.LoginReq;
import com.rag.cn.yuetaoragbackend.dto.resp.LoginResp;
import com.rag.cn.yuetaoragbackend.framework.context.LoginUser;
import com.rag.cn.yuetaoragbackend.framework.exception.ClientException;
import com.rag.cn.yuetaoragbackend.framework.security.PasswordHashVerifier;
import com.rag.cn.yuetaoragbackend.framework.web.Results;
import com.rag.cn.yuetaoragbackend.framework.convention.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserMapper userMapper;
    private final AuthProperties authProperties;

    @PostMapping("/login")
    public Result<LoginResp> login(@Valid @RequestBody LoginReq requestParam) {
        if (requestParam == null || !StringUtils.hasText(requestParam.getUsername())
                || !StringUtils.hasText(requestParam.getPassword())) {
            throw new ClientException("用户名或密码不能为空");
        }
        UserDO userDO = userMapper.selectOne(Wrappers.<UserDO>lambdaQuery()
                .eq(UserDO::getUsername, requestParam.getUsername())
                .eq(UserDO::getDeleteFlag, DeleteFlagEnum.NORMAL.getCode()));
        if (userDO == null) {
            throw new ClientException("用户名或密码错误");
        }
        if (!CommonStatusEnum.ENABLED.getCode().equals(userDO.getStatus())) {
            throw new ClientException("用户已停用");
        }
        if (!PasswordHashVerifier.matches(requestParam.getPassword(), userDO.getPasswordHash())) {
            throw new ClientException("用户名或密码错误");
        }

        LoginUser loginUser = LoginUser.builder()
                .userId(String.valueOf(userDO.getId()))
                .username(userDO.getUsername())
                .role(userDO.getRoleCode())
                .build();
        boolean rememberMe = Boolean.TRUE.equals(requestParam.getRememberMe());
        SaLoginParameter loginParameter = new SaLoginParameter();
        if (rememberMe) {
            // 7 天免登录靠 active-timeout 做滑动续期，timeout 只负责兜底硬过期。
            loginParameter.setTimeout(authProperties.getRememberMeTimeoutSeconds())
                    .setActiveTimeout(authProperties.getRememberMeActiveTimeoutSeconds())
                    .setIsLastingCookie(true);
        } else {
            // 当前会话模式不要求长期留存，只保留短期活跃窗口即可。
            loginParameter.setTimeout(authProperties.getSessionTimeoutSeconds())
                    .setActiveTimeout(authProperties.getSessionActiveTimeoutSeconds())
                    .setIsLastingCookie(false);
        }
        StpUtil.login(userDO.getId(), loginParameter);
        StpUtil.getTokenSession().set(authProperties.getTokenSessionLoginUserKey(), JSONUtil.toJsonStr(loginUser));

        return Results.success(new LoginResp()
                .setUserId(String.valueOf(userDO.getId()))
                .setUsername(userDO.getUsername())
                .setDisplayName(userDO.getDisplayName())
                .setRole(userDO.getRoleCode())
                .setToken(StpUtil.getTokenValue()));
    }

    @PostMapping("/logout")
    public Result<Void> logout() {
        if (StpUtil.isLogin()) {
            StpUtil.logout();
        }
        return Results.success();
    }
}
