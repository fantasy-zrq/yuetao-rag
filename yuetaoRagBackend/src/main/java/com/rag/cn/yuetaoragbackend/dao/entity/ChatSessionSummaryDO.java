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
@TableName("t_chat_session_summary")
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class ChatSessionSummaryDO extends BaseDO {

    /** 所属会话ID。 */
    private Long sessionId;

    /** 摘要内容。 */
    private String summaryText;

    /** 摘要版本号。 */
    private Integer summaryVersion;

    /** 摘要覆盖到的消息顺序号。 */
    private Integer sourceMessageSeq;
}
