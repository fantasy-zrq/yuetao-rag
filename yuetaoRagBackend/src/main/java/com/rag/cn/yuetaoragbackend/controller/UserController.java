package com.rag.cn.yuetaoragbackend.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.rag.cn.yuetaoragbackend.config.enums.DeleteFlagEnum;
import com.rag.cn.yuetaoragbackend.dao.entity.UserDO;
import com.rag.cn.yuetaoragbackend.dao.mapper.UserMapper;
import com.rag.cn.yuetaoragbackend.dto.resp.CurrentUserResp;
import com.rag.cn.yuetaoragbackend.framework.context.UserContext;
import com.rag.cn.yuetaoragbackend.framework.convention.Result;
import com.rag.cn.yuetaoragbackend.framework.web.Results;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserMapper userMapper;

    @GetMapping("/me")
    public Result<CurrentUserResp> currentUser() {
        Long userId = Long.parseLong(UserContext.requireUser().getUserId());
        UserDO userDO = userMapper.selectOne(Wrappers.<UserDO>lambdaQuery()
                .eq(UserDO::getId, userId)
                .eq(UserDO::getDeleteFlag, DeleteFlagEnum.NORMAL.getCode()));
        if (userDO == null) {
            return Results.success(new CurrentUserResp()
                    .setUserId(String.valueOf(userId))
                    .setUsername(UserContext.getUsername())
                    .setRole(UserContext.getRole()));
        }
        return Results.success(new CurrentUserResp()
                .setUserId(String.valueOf(userDO.getId()))
                .setUsername(userDO.getUsername())
                .setDisplayName(userDO.getDisplayName())
                .setRole(userDO.getRoleCode()));
    }
}
