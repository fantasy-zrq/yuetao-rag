package com.rag.cn.yuetaoragbackend.service.impl;

import com.rag.cn.yuetaoragbackend.config.OpenAiCompatibleRerankModel;
import com.rag.cn.yuetaoragbackend.config.properties.AiProperties;
import com.rag.cn.yuetaoragbackend.config.properties.AuthzProperties;
import com.rag.cn.yuetaoragbackend.config.properties.RagRetrievalProperties;
import com.rag.cn.yuetaoragbackend.config.record.RerankResultRecord;
import com.rag.cn.yuetaoragbackend.dao.entity.UserDO;
import com.rag.cn.yuetaoragbackend.dao.mapper.ChunkVectorMapper;
import com.rag.cn.yuetaoragbackend.dao.projection.RetrievedChunk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * @author zrq
 * 2026/04/29 15:40
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagRetrievalService {

    private final ChunkVectorMapper chunkVectorMapper;
    private final EmbeddingModel embeddingModel;
    private final RagRetrievalProperties retrievalProperties;
    private final AiProperties aiProperties;
    private final AuthzProperties authzProperties;
    private final OpenAiCompatibleRerankModel rerankModel;

    public List<RetrievedChunk> retrieve(UserDO user, String query) {
        return retrieveInternal(user, query, null);
    }

    public List<RetrievedChunk> retrieveByCollection(UserDO user, String query, String collectionName) {
        return retrieve(user, query);
    }

    public List<RetrievedChunk> retrieveByKnowledgeBaseIds(UserDO user, String query, List<Long> knowledgeBaseIds) {
        return retrieveInternal(user, query, knowledgeBaseIds);
    }

    private List<RetrievedChunk> retrieveInternal(UserDO user, String query, List<Long> knowledgeBaseIds) {
        float[] queryVector = embeddingModel.embed(query);
        String vectorLiteral = toVectorLiteral(queryVector);
        int recallLimit = Math.max(1, safeInt(retrievalProperties.getTopK(), 8)
                * Math.max(1, safeInt(retrievalProperties.getCandidateMultiplier(), 3)));

        boolean hasKnowledgeBaseIds = !CollectionUtils.isEmpty(knowledgeBaseIds);
        boolean admin = isAdmin(user);

        log.info("[RETRIEVE] 向量检索: queryLen={}, scope={}, recallLimit={}, vectorDim={}",
                query.length(), hasKnowledgeBaseIds ? knowledgeBaseIds.toString() : "global", recallLimit, queryVector.length);

        return chunkVectorMapper.selectByVectorSearch(
                vectorLiteral,
                admin,
                user.getRankLevel() == null ? 0 : user.getRankLevel(),
                user.getDepartmentId() == null ? -1L : user.getDepartmentId(),
                hasKnowledgeBaseIds,
                hasKnowledgeBaseIds ? knowledgeBaseIds : List.of(),
                recallLimit);
    }

    public List<RetrievedChunk> rerank(String query, List<RetrievedChunk> recalled) {
        if (recalled.isEmpty()) {
            return List.of();
        }
        int rerankLimit = Math.min(
                Math.max(1, safeInt(aiProperties.getRerank().getTopN(), safeInt(retrievalProperties.getTopK(), 8))),
                recalled.size());
        List<String> documents = recalled.stream().map(RetrievedChunk::effectiveContent).toList();
        log.info("[RERANK] 重排序开始: queryLen={}, docCount={}, rerankLimit={}", query.length(), documents.size(), rerankLimit);
        try {
            List<RerankResultRecord> rerankResults = rerankModel.rerank(query, documents, rerankLimit);
            if (rerankResults.isEmpty()) {
                return List.of();
            }
            List<RetrievedChunk> reranked = new ArrayList<>();
            for (RerankResultRecord each : rerankResults) {
                int index = each.index();
                if (index < 0 || index >= recalled.size()) {
                    continue;
                }
                RetrievedChunk original = recalled.get(index);
                reranked.add(original.withScores(each.score(), each.score()));
            }
            List<RetrievedChunk> result = reranked.stream()
                    .sorted(Comparator.comparing(RetrievedChunk::finalScore).reversed())
                    .toList();
            log.info("[RERANK] 重排序完成: resultCount={}, topScores={}", result.size(),
                    result.stream().limit(3).map(c -> String.format("{idx=%d,score=%.4f}", recalled.indexOf(c), c.finalScore())).toList());
            return result;
        } catch (RuntimeException ex) {
            log.warn("[RERANK] 重排序模型不可用，降级使用向量分数，返回前{}条", rerankLimit, ex);
            return recalled.stream()
                    .limit(rerankLimit)
                    .map(each -> each.withScores(each.vectorScore(), each.vectorScore()))
                    .toList();
        }
    }

    private boolean isAdmin(UserDO user) {
        if (user == null || !StringUtils.hasText(user.getRoleCode())) {
            return false;
        }
        return authzProperties.getAdminRoleCodes().stream()
                .filter(StringUtils::hasText)
                .anyMatch(each -> each.equalsIgnoreCase(user.getRoleCode()));
    }

    private String toVectorLiteral(float[] vector) {
        List<String> values = new ArrayList<>(vector.length);
        for (float each : vector) {
            values.add(Float.toString(each));
        }
        return "[" + String.join(",", values) + "]";
    }

    private int safeInt(Integer value, int defaultValue) {
        return value == null || value <= 0 ? defaultValue : value;
    }
}
