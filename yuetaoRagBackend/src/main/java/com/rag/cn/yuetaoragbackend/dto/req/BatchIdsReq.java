package com.rag.cn.yuetaoragbackend.dto.req;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * @author zrq
 * 2026/05/11
 */
@Data
@Accessors(chain = true)
public class BatchIdsReq {

    /** 操作的ID列表。 */
    @NotEmpty(message = "IDs不能为空")
    private List<Long> ids;
}
