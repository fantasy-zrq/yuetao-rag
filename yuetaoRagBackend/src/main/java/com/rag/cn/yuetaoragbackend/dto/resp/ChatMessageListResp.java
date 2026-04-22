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
public class ChatMessageListResp {

    /** 消息ID。 */
    private Long id;

    /** 消息角色。 */
    private String role;

    /** 消息内容。 */
    private String content;

    /** 消息内容类型。 */
    private String contentType;

    /** 顺序号。 */
    private Integer sequenceNo;

    /** 创建时间。 */
    private Date createTime;
}
