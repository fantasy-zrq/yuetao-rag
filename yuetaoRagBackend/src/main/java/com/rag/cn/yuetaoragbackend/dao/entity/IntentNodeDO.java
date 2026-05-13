package com.rag.cn.yuetaoragbackend.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * @author zrq
 * 2026/05/11
 */
@Data
@TableName("t_intent_node")
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class IntentNodeDO extends BaseDO {

    /** 意图标识，业务唯一。 */
    private String intentCode;

    /** 节点名称。 */
    private String name;

    /** 层级：0-DOMAIN，1-CATEGORY，2-TOPIC。 */
    private Integer level;

    /** 父节点ID，根节点为0。 */
    private Long parentId;

    /** 父节点意图标识（冗余），根节点为null。 */
    private String parentCode;

    /** 节点描述。 */
    private String description;

    /** 示例问题，JSON数组字符串。 */
    private String examples;

    /** 向量库Collection名称（kind=KB时）。 */
    private String collectionName;

    /** 绑定知识库ID（kind=KB时）。 */
    private Long kbId;

    /** MCP工具ID（kind=MCP时）。 */
    private String mcpToolId;

    /** 节点级TopK，null时使用全局。 */
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

    /** 参数提取提示词模板（MCP专属）。 */
    private String paramPromptTemplate;

    /** 创建人用户ID。 */
    private Long createdBy;

    /** 最后更新人用户ID。 */
    private Long updatedBy;
}
