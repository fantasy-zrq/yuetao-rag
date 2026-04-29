package com.rag.cn.yuetaoragbackend.dto.resp;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @author zrq
 * 2026/04/29 15:40
 */
@Data
@Accessors(chain = true)
public class ChatCitationResp {

    /** 引用序号。 */
    private Integer index;

    /** 文档ID。 */
    private Long documentId;

    /** 文档标题。 */
    private String documentTitle;

    /** 切片ID。 */
    private Long chunkId;

    /** 切片顺序号。 */
    private Integer chunkNo;

    /** 引用展示标签。 */
    private String referenceLabel;

    /** 摘要片段。 */
    private String snippet;
}
