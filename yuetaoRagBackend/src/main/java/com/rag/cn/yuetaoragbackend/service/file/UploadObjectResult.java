package com.rag.cn.yuetaoragbackend.service.file;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author zrq
 * 2026/04/26 16:20
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UploadObjectResult {

    private String etag;

    private String url;
}
