package com.rag.cn.yuetaoragbackend.dto.resp;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * @author zrq
 * 2026/05/11
 */
@Data
@Accessors(chain = true)
public class IntentNodeTreeResp {

    private Long id;
    private String intentCode;
    private String name;
    private Integer level;
    private String parentCode;
    private String description;
    private String examples;
    private String collectionName;
    private Long kbId;
    private String mcpToolId;
    private Integer topK;
    private Integer kind;
    private Integer sortOrder;
    private Integer enabled;
    private String promptSnippet;
    private String promptTemplate;
    private String paramPromptTemplate;

    /** 子节点列表（递归）。 */
    private List<IntentNodeTreeResp> children;
}
