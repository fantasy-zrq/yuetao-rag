package com.rag.cn.yuetaoragbackend.dto.resp;

import java.util.Date;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @author zrq
 * 2026/05/07
 */
@Data
@Accessors(chain = true)
public class KnowledgeDocumentChunkLogResp {

    private Long id;

    private Long documentId;

    private Long knowledgeBaseId;

    private String operationType;

    private String status;

    private String chunkMode;

    private Integer chunkCount;

    private Long splitCostMillis;

    private Long vectorCostMillis;

    private Long totalCostMillis;

    private String errorMessage;

    private Date startTime;

    private Date endTime;
}
