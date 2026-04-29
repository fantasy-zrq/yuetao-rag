package com.rag.cn.yuetaoragbackend.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.cn.yuetaoragbackend.config.properties.AiProperties;
import com.rag.cn.yuetaoragbackend.config.properties.AuthzProperties;
import com.rag.cn.yuetaoragbackend.config.properties.RagRetrievalProperties;
import com.rag.cn.yuetaoragbackend.dao.entity.UserDO;
import com.rag.cn.yuetaoragbackend.framework.errorcode.BaseErrorCode;
import com.rag.cn.yuetaoragbackend.framework.exception.RemoteException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/**
 * @author zrq
 * 2026/04/29 15:40
 */
@Service
@RequiredArgsConstructor
public class RagRetrievalService {

    private final JdbcTemplate jdbcTemplate;
    private final EmbeddingModel embeddingModel;
    private final RagRetrievalProperties retrievalProperties;
    private final AiProperties aiProperties;
    private final AuthzProperties authzProperties;
    private final ObjectMapper objectMapper;

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
                vectorLiteral,
                recallLimit);
        return recalled;
    }

    public List<RetrievedChunk> rerank(String query, List<RetrievedChunk> recalled) {
        if (recalled.isEmpty()) {
            return List.of();
        }
        AiProperties.RerankCandidateProperties candidate = resolveRerankCandidate();
        AiProperties.ProviderProperties provider = aiProperties.getProviders().get(candidate.getProvider());
        if (provider == null) {
            throw new RemoteException("未找到重排序模型提供商配置：" + candidate.getProvider(), BaseErrorCode.REMOTE_ERROR);
        }
        if (!StringUtils.hasText(provider.getEndpoints().getRerank())) {
            throw new RemoteException("未配置重排序接口路径：" + candidate.getProvider(), BaseErrorCode.REMOTE_ERROR);
        }

        int rerankLimit = Math.min(
                Math.max(1, safeInt(aiProperties.getRerank().getTopN(), safeInt(retrievalProperties.getTopK(), 8))),
                recalled.size());
        List<String> documents = recalled.stream()
                .map(RetrievedChunk::effectiveContent)
                .toList();

        LinkedHashMap<String, Object> input = new LinkedHashMap<>();
        input.put("query", query);
        input.put("documents", documents);

        LinkedHashMap<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("top_n", rerankLimit);
        parameters.put("return_documents", Boolean.TRUE);

        LinkedHashMap<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", candidate.getModel());
        requestBody.put("input", input);
        requestBody.put("parameters", parameters);

        RestClient restClient = RestClient.builder()
                .baseUrl(provider.getUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + provider.getApiKey())
                .build();
        JsonNode root = restClient.post()
                .uri(provider.getEndpoints().getRerank())
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(JsonNode.class);
        if (root == null) {
            throw new RemoteException("重排序模型返回为空", BaseErrorCode.REMOTE_ERROR);
        }
        JsonNode resultsNode = root.path("output").path("results");
        if (!resultsNode.isArray() || resultsNode.size() == 0) {
            return List.of();
        }

        List<RetrievedChunk> reranked = new ArrayList<>();
        for (JsonNode each : resultsNode) {
            int index = each.path("index").asInt(-1);
            if (index < 0 || index >= recalled.size()) {
                continue;
            }
            RetrievedChunk original = recalled.get(index);
            double score = each.path("relevance_score").asDouble(0D);
            reranked.add(original.withScores(score, score));
        }
        return reranked.stream()
                .sorted(Comparator.comparing(RetrievedChunk::finalScore).reversed())
                .toList();
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

    private AiProperties.RerankCandidateProperties resolveRerankCandidate() {
        return aiProperties.getRerank().getCandidates().stream()
                .filter(each -> Objects.equals(each.getId(), aiProperties.getRerank().getDefaultModel()))
                .findFirst()
                .orElseThrow(() -> new RemoteException("未找到默认重排序模型配置：" + aiProperties.getRerank().getDefaultModel(),
                        BaseErrorCode.REMOTE_ERROR));
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
