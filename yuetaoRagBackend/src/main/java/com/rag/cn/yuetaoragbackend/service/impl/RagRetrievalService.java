package com.rag.cn.yuetaoragbackend.service.impl;

import com.rag.cn.yuetaoragbackend.config.OpenAiCompatibleRerankModel;
import com.rag.cn.yuetaoragbackend.config.record.RerankResultRecord;
import com.rag.cn.yuetaoragbackend.config.properties.AiProperties;
import com.rag.cn.yuetaoragbackend.config.properties.AuthzProperties;
import com.rag.cn.yuetaoragbackend.config.properties.RagRetrievalProperties;
import com.rag.cn.yuetaoragbackend.dao.entity.UserDO;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * @author zrq
 * 2026/04/29 15:40
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagRetrievalService {

    private final JdbcTemplate jdbcTemplate;
    private final EmbeddingModel embeddingModel;
    private final RagRetrievalProperties retrievalProperties;
    private final AiProperties aiProperties;
    private final AuthzProperties authzProperties;
    private final OpenAiCompatibleRerankModel rerankModel;

    public List<RetrievedChunk> retrieve(UserDO user, String query) {
        float[] queryVector = embeddingModel.embed(query);
        String vectorLiteral = toVectorLiteral(queryVector);
        int recallLimit = Math.max(1, safeInt(retrievalProperties.getTopK(), 8)
                * Math.max(1, safeInt(retrievalProperties.getCandidateMultiplier(), 3)));

        String sql = """
                select c.id as chunk_id,
                       c.document_id,
                       c.chunk_no,
                       c.effective_content,
                       kd.title as document_title,
                       1 - (cv.embedding <=> cast(? as vector)) as vector_score
                from t_chunk_vector cv
                join t_chunk c on c.id::text = cv.id
                join t_knowledge_document kd on kd.id = c.document_id
                where c.delete_flag = 0
                  and c.enabled = true
                  and c.embedding_status = 'SUCCESS'
                  and kd.delete_flag = 0
                  and kd.status = 'ENABLED'
                  and kd.parse_status = 'SUCCESS'
                  and (? = true or coalesce(kd.min_rank_level, 0) <= ?)
                  and (? = true
                       or kd.visibility_scope <> 'SENSITIVE'
                       or exists (
                           select 1
                           from t_document_department_auth dda
                           where dda.document_id = kd.id
                             and dda.department_id = ?
                             and dda.delete_flag = 0
                       ))
                order by cv.embedding <=> cast(? as vector)
                limit ?
                """;

        boolean admin = isAdmin(user);
        List<RetrievedChunk> recalled = jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new RetrievedChunk(
                        rs.getLong("chunk_id"),
                        rs.getLong("document_id"),
                        rs.getString("document_title"),
                        rs.getInt("chunk_no"),
                        rs.getString("effective_content"),
                        rs.getDouble("vector_score"),
                        0D,
                        0D),
                vectorLiteral,
                admin,
                user.getRankLevel() == null ? 0 : user.getRankLevel(),
                admin,
                user.getDepartmentId() == null ? -1L : user.getDepartmentId(),
                vectorLiteral,
                recallLimit);
        return recalled;
    }

    public List<RetrievedChunk> rerank(String query, List<RetrievedChunk> recalled) {
        if (recalled.isEmpty()) {
            return List.of();
        }
        int rerankLimit = Math.min(
                Math.max(1, safeInt(aiProperties.getRerank().getTopN(), safeInt(retrievalProperties.getTopK(), 8))),
                recalled.size());
        List<String> documents = recalled.stream().map(RetrievedChunk::effectiveContent).toList();
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
            return reranked.stream()
                    .sorted(Comparator.comparing(RetrievedChunk::finalScore).reversed())
                    .toList();
        } catch (RuntimeException ex) {
            log.warn("重排序模型不可用，降级为直接使用召回结果", ex);
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

    public record RetrievedChunk(Long chunkId, Long documentId, String documentTitle, Integer chunkNo,
                                 String effectiveContent, Double vectorScore, Double lexicalScore,
                                 Double finalScore) {

        public RetrievedChunk withScores(Double nextLexicalScore, Double nextFinalScore) {
            return new RetrievedChunk(
                    this.chunkId,
                    this.documentId,
                    this.documentTitle,
                    this.chunkNo,
                    this.effectiveContent,
                    this.vectorScore,
                    nextLexicalScore,
                    nextFinalScore);
        }
    }
}
