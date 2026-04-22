package com.rag.cn.yuetaoragbackend.dto.resp;

import java.util.Date;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @author zrq
 * 2026/04/22 16:00
 */
@Data
@Accessors(chain = true)
public class ChatMessageCreateResp {

    /** 消息ID。 */
    private Long id;

    /** 所属会话ID。 */
    private Long sessionId;

    /** 所属用户ID。 */
    private Long userId;

    /** 消息角色。 */
    private String role;

    /** 消息内容类型。 */
    private String contentType;

    /** 顺序号。 */
    private Integer sequenceNo;

    /** 链路追踪ID。 */
    private String traceId;

    /** 创建时间。 */
    private Date createTime;
}
