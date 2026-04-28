package com.rag.cn.yuetaoragbackend.dao.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.rag.cn.yuetaoragbackend.framework.database.JsonStringTypeHandler;
import java.util.Date;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * @author zrq
 * 2026/04/28 00:30
 */
@Data
@TableName(value = "t_document_chunk_log", autoResultMap = true)
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class DocumentChunkLogDO extends BaseDO {

    /** 文档ID。 */
    private Long documentId;

    /** 知识库ID。 */
    private Long knowledgeBaseId;

    /** 操作类型。 */
    private String operationType;

    /** 执行状态。 */
    private String status;

    /** 分块模式。 */
    private String chunkMode;

    /** 分块配置 JSON 快照。 */
    @TableField(typeHandler = JsonStringTypeHandler.class)
    private String chunkConfig;

    /** 本次生成分块数量。 */
    private Integer chunkCount;

    /** 解析与分块耗时，单位毫秒。 */
    private Long splitCostMillis;

    /** 向量化写入耗时，单位毫秒。 */
    private Long vectorCostMillis;

    /** 总耗时，单位毫秒。 */
    private Long totalCostMillis;

    /** 错误信息。 */
    private String errorMessage;

    /** 执行开始时间。 */
    private Date startTime;

    /** 执行结束时间。 */
    private Date endTime;

    /** 创建人用户ID。 */
    private Long createdBy;

    /** 更新人用户ID。 */
    private Long updatedBy;
}
