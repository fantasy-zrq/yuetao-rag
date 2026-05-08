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

    /** 引用序号，从1开始。 */
    private Integer index;

    /** 文档ID。 */
    private Long documentId;

    /** 文档标题。 */
    private String documentTitle;

    /** 切片ID。 */
    private Long chunkId;

    /** 切片顺序号。 */
    private Integer chunkNo;

    /** 引用展示标签，格式为"文档标题（切片#N）"。 */
    private String referenceLabel;

    /** 摘要片段，截取切片内容前120字符。 */
    private String snippet;
}
