package com.rag.cn.yuetaoragbackend.dto.resp;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class CurrentUserResp {

    private String userId;

    private String username;

    private String displayName;

    private String role;
}
