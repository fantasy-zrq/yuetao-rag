package com.rag.cn.yuetaoragbackend.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @author zrq
 * 2026/05/11
 */
@Data
@Accessors(chain = true)
public class CreateIntentNodeReq {

    /** 意图标识，业务唯一。 */
    @NotBlank(message = "意图标识不能为空")
    private String intentCode;

    /** 节点名称。 */
    @NotBlank(message = "节点名称不能为空")
    private String name;

    /** 层级。 */
    @NotNull(message = "层级不能为空")
    private Integer level;

    /** 父节点意图标识，null或空表示根节点。 */
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

    /** 节点类型：0-KB，1-SYSTEM，2-MCP。 */
    private Integer kind;

    /** 同级排序号。 */
    private Integer sortOrder;

    /** 是否启用：1-启用，0-停用。 */
    private Integer enabled;

    /** 短规则片段。 */
    private String promptSnippet;

    /** Prompt模板。 */
    private String promptTemplate;

    /** 参数提取提示词模板。 */
    private String paramPromptTemplate;
}
