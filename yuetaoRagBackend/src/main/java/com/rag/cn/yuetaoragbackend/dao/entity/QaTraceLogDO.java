package com.rag.cn.yuetaoragbackend.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * @author zrq
 * 2026/04/22 14:45
 */
@Data
@TableName("t_qa_trace_log")
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class QaTraceLogDO extends BaseDO {

    /** 链路追踪ID。 */
    private String traceId;

    /** 所属会话ID。 */
    private Long sessionId;

    /** 所属用户ID。 */
    private Long userId;

    /** 链路阶段。 */
    private String stage;

    /** 阶段状态。 */
    private String status;

    /** 阶段耗时，单位毫秒。 */
    private Long latencyMs;

    /** 扩展载荷引用。 */
    private String payloadRef;
}
