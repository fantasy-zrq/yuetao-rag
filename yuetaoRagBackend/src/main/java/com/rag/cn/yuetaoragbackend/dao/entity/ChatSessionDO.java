package com.rag.cn.yuetaoragbackend.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * @author zrq
 * 2026/04/22 14:45
 */
@Data
@TableName("t_chat_session")
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class ChatSessionDO extends BaseDO {

    /** 所属用户ID。 */
    private Long userId;

    /** 会话标题。 */
    private String title;

    /** 会话状态。 */
    private String status;

    /** 最近活跃时间。 */
    private Date lastActiveAt;
}
