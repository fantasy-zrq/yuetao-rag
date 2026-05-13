package com.rag.cn.yuetaoragbackend.config.record;

import com.rag.cn.yuetaoragbackend.dto.resp.IntentNodeTreeResp;

/**
 * 一次意图叶子节点评分命中结果。
 */
public record IntentScoreMatchRecord(IntentNodeTreeResp leaf, double score) {
}
