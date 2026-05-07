package com.rag.cn.yuetaoragbackend.dto.resp;

import java.util.Date;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @author zrq
 * 2026/04/22 16:00
 */
@Data
@Accessors(chain = true)
public class KnowledgeDocumentListResp {

    /** 文档ID。 */
    private Long id;

    /** 所属知识库ID。 */
    private Long knowledgeBaseId;

    /** 文档标题。 */
    private String title;

    /** 文档解析状态。 */
    private String parseStatus;

    /** 文档解析失败原因。 */
    private String failReason;

    /** 分块模式。 */
    private String chunkMode;

    /** 文档状态。 */
    private String status;

    /** 文件大小。 */
    private Long fileSize;

    /** 当前分块数量。 */
    private Integer chunkCount;

    /** 更新时间。 */
    private Date updateTime;
}
