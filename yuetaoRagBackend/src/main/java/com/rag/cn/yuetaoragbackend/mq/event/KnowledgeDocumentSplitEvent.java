package com.rag.cn.yuetaoragbackend.mq.event;

import java.io.Serial;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author zrq
 * 2026/04/27 10:00
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeDocumentSplitEvent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long documentId;
}
