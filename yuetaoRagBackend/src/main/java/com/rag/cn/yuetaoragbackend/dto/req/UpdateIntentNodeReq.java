package com.rag.cn.yuetaoragbackend.dto.req;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @author zrq
 * 2026/05/11
 */
@Data
@Accessors(chain = true)
public class UpdateIntentNodeReq {

    /** 节点名称。 */
    private String name;

    /** 层级。 */
    private Integer level;

    /** 父节点意图标识。 */
    private String parentCode;

    /** 节点描述。 */
    private String description;

    /** 示例问题数组。 */
    private String[] examples;

    /** 向量库Collection名称。 */
    private String collectionName;

    /** MCP工具ID。 */
    private String mcpToolId;

    /** 节点级TopK。 */
    private Integer topK;

    /** 节点类型。 */
    private Integer kind;

    /** 同级排序号。 */
    private Integer sortOrder;

    /** 是否启用。 */
    private Integer enabled;

    /** 短规则片段。 */
    private String promptSnippet;

    /** Prompt模板。 */
    private String promptTemplate;

    /** 参数提取提示词模板。 */
    private String paramPromptTemplate;
}
