package com.rag.cn.yuetaoragbackend.config.record;

import com.rag.cn.yuetaoragbackend.dto.resp.ChatCitationResp;

import java.util.List;

/**
 * 同步问答链路最终返回给前端与持久化层的答案结果。
 */
public record ChatAnswerResultRecord(String answer, List<ChatCitationResp> citations, boolean knowledgeHit) {

    public ChatAnswerResultRecord {
        citations = citations == null ? List.of() : List.copyOf(citations);
    }
}
