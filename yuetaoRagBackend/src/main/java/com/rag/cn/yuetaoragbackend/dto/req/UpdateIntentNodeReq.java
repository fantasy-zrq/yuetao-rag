package com.rag.cn.yuetaoragbackend.dto.req;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.HashSet;
import java.util.Set;

/**
 * @author zrq
 * 2026/05/11
 */
@Data
@Accessors(chain = true)
public class UpdateIntentNodeReq {

    @JsonIgnore
    private final Set<String> providedFields = new HashSet<>();

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

    /** 绑定知识库ID。 */
    private Long kbId;

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

    @JsonIgnore
    public boolean hasDescription() {
        return providedFields.contains("description");
    }

    @JsonIgnore
    public boolean hasExamples() {
        return providedFields.contains("examples");
    }

    @JsonIgnore
    public boolean hasCollectionName() {
        return providedFields.contains("collectionName");
    }

    @JsonIgnore
    public boolean hasKbId() {
        return providedFields.contains("kbId");
    }

    @JsonIgnore
    public boolean hasMcpToolId() {
        return providedFields.contains("mcpToolId");
    }

    @JsonIgnore
    public boolean hasTopK() {
        return providedFields.contains("topK");
    }

    @JsonIgnore
    public boolean hasPromptSnippet() {
        return providedFields.contains("promptSnippet");
    }

    @JsonIgnore
    public boolean hasPromptTemplate() {
        return providedFields.contains("promptTemplate");
    }

    @JsonIgnore
    public boolean hasParamPromptTemplate() {
        return providedFields.contains("paramPromptTemplate");
    }

    @JsonSetter("description")
    public UpdateIntentNodeReq setDescription(String description) {
        providedFields.add("description");
        this.description = description;
        return this;
    }

    @JsonSetter("examples")
    public UpdateIntentNodeReq setExamples(String[] examples) {
        providedFields.add("examples");
        this.examples = examples;
        return this;
    }

    @JsonSetter("collectionName")
    public UpdateIntentNodeReq setCollectionName(String collectionName) {
        providedFields.add("collectionName");
        this.collectionName = collectionName;
        return this;
    }

    @JsonSetter("kbId")
    public UpdateIntentNodeReq setKbId(Long kbId) {
        providedFields.add("kbId");
        this.kbId = kbId;
        return this;
    }

    @JsonSetter("mcpToolId")
    public UpdateIntentNodeReq setMcpToolId(String mcpToolId) {
        providedFields.add("mcpToolId");
        this.mcpToolId = mcpToolId;
        return this;
    }

    @JsonSetter("topK")
    public UpdateIntentNodeReq setTopK(Integer topK) {
        providedFields.add("topK");
        this.topK = topK;
        return this;
    }

    @JsonSetter("promptSnippet")
    public UpdateIntentNodeReq setPromptSnippet(String promptSnippet) {
        providedFields.add("promptSnippet");
        this.promptSnippet = promptSnippet;
        return this;
    }

    @JsonSetter("promptTemplate")
    public UpdateIntentNodeReq setPromptTemplate(String promptTemplate) {
        providedFields.add("promptTemplate");
        this.promptTemplate = promptTemplate;
        return this;
    }

    @JsonSetter("paramPromptTemplate")
    public UpdateIntentNodeReq setParamPromptTemplate(String paramPromptTemplate) {
        providedFields.add("paramPromptTemplate");
        this.paramPromptTemplate = paramPromptTemplate;
        return this;
    }
}
