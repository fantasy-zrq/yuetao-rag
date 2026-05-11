package com.rag.cn.yuetaoragbackend.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.rag.cn.yuetaoragbackend.config.record.RerankResultRecord;
import com.rag.cn.yuetaoragbackend.framework.errorcode.BaseErrorCode;
import com.rag.cn.yuetaoragbackend.framework.exception.RemoteException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/**
 * @author zrq
 * 2026/04/29 18:55
 */
public class OpenAiCompatibleRerankModel {

    private final String baseUrl;
    private final String apiKey;
    private final String rerankPath;
    private final String modelName;
    private final RestClient restClient;

    public OpenAiCompatibleRerankModel(String baseUrl, String apiKey, String rerankPath, String modelName) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.rerankPath = rerankPath;
        this.modelName = modelName;
        this.restClient = RestClient.builder()
                .baseUrl(StringUtils.hasText(baseUrl) ? baseUrl : "")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + (apiKey == null ? "" : apiKey))
                .build();
    }

    public List<RerankResultRecord> rerank(String query, List<String> documents, int topN) {
        if (!StringUtils.hasText(baseUrl) || !StringUtils.hasText(apiKey)
                || !StringUtils.hasText(rerankPath) || !StringUtils.hasText(modelName)) {
            throw new RemoteException("重排序模型配置不完整", BaseErrorCode.REMOTE_ERROR);
        }

        LinkedHashMap<String, Object> input = new LinkedHashMap<>();
        input.put("query", query);
        input.put("documents", documents);

        LinkedHashMap<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("top_n", topN);
        parameters.put("return_documents", Boolean.FALSE);

        LinkedHashMap<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", modelName);
        requestBody.put("input", input);
        requestBody.put("parameters", parameters);

        JsonNode root = restClient.post()
                .uri(rerankPath)
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

        List<RerankResultRecord> results = new ArrayList<>();
        for (JsonNode each : resultsNode) {
            results.add(new RerankResultRecord(
                    each.path("index").asInt(-1),
                    each.path("relevance_score").asDouble(0D)));
        }
        return results;
    }
}
