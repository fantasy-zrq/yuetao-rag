package com.rag.cn.yuetaoragbackend.dto.resp;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @author zrq
 * 2026/04/29 23:30
 */
@Data
@Accessors(chain = true)
public class ChatStreamCitationResp {

    private Integer index;

    private Long documentId;

    private String documentTitle;

    private Long chunkId;

    private Integer chunkNo;

    private String referenceLabel;

    private String snippet;
}
