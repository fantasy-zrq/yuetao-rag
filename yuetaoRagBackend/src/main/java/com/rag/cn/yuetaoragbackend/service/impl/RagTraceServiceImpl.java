package com.rag.cn.yuetaoragbackend.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rag.cn.yuetaoragbackend.dao.mapper.QaTraceLogMapper;
import com.rag.cn.yuetaoragbackend.dto.req.PageRunsReq;
import com.rag.cn.yuetaoragbackend.dto.resp.PageResp;
import com.rag.cn.yuetaoragbackend.dto.resp.RagTraceDetailResp;
import com.rag.cn.yuetaoragbackend.dto.resp.RagTraceNodeResp;
import com.rag.cn.yuetaoragbackend.dto.resp.RagTraceRunResp;
import com.rag.cn.yuetaoragbackend.service.RagTraceService;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * @author zrq
 * 2026/05/07
 */
@Service
@RequiredArgsConstructor
public class RagTraceServiceImpl implements RagTraceService {

    private final QaTraceLogMapper qaTraceLogMapper;
    private final ObjectMapper objectMapper;

    @Override
    public PageResp<RagTraceRunResp> pageRuns(PageRunsReq requestParam) {
        long page = Math.max(1L, requestParam.getCurrent());
        long pageSize = Math.max(1L, requestParam.getSize());
        String traceId = requestParam.getTraceId();
        String traceIdPattern = (traceId == null || traceId.isBlank()) ? null : "%" + traceId.trim() + "%";

        Page<Map<String, Object>> pageParam = new Page<>(page, pageSize);
        IPage<Map<String, Object>> result = qaTraceLogMapper.selectPageRuns(pageParam, traceIdPattern);

        List<RagTraceRunResp> records = result.getRecords().stream()
                .map(this::toRunResp)
                .toList();
        long total = result.getTotal();
        long pages = total == 0 ? 0 : (total + pageSize - 1) / pageSize;
        return new PageResp<RagTraceRunResp>()
                .setRecords(records)
                .setTotal(total)
                .setSize(pageSize)
                .setCurrent(page)
                .setPages(pages);
    }

    @Override
    public RagTraceDetailResp detail(String traceId) {
        Map<String, Object> row = qaTraceLogMapper.selectDetailByTraceId(traceId);
        RagTraceRunResp run = toRunResp(row);
        List<RagTraceNodeResp> nodes = nodes(traceId);
        return new RagTraceDetailResp().setRun(run).setNodes(nodes);
    }

    @Override
    public List<RagTraceNodeResp> nodes(String traceId) {
        List<Map<String, Object>> rows = qaTraceLogMapper.selectNodesByTraceId(traceId);
        return rows.stream().map(this::toNodeResp).toList();
    }

    private RagTraceRunResp toRunResp(Map<String, Object> row) {
        if (row == null || row.isEmpty()) {
            return null;
        }
        Long userId = row.get("user_id") == null ? null : ((Number) row.get("user_id")).longValue();
        Long sessionId = row.get("session_id") == null ? null : ((Number) row.get("session_id")).longValue();
        Long durationMs = row.get("duration_ms") == null ? null : ((Number) row.get("duration_ms")).longValue();
        return new RagTraceRunResp()
                .setTraceId(String.valueOf(row.get("trace_id")))
                .setTraceName(String.valueOf(row.get("trace_id")))
                .setConversationId(sessionId)
                .setTaskId(null)
                .setUserId(userId)
                .setUsername(null)
                .setStatus(row.get("status") == null ? null : String.valueOf(row.get("status")))
                .setDurationMs(durationMs)
                .setStartTime((Date) row.get("start_time"))
                .setEndTime((Date) row.get("end_time"));
    }

    private RagTraceNodeResp toNodeResp(Map<String, Object> row) {
        Date startTime = (Date) row.get("create_time");
        Date nextStartTime = (Date) row.get("next_create_time");
        Long duration = resolveNodeDuration(row.get("latency_ms"), startTime, nextStartTime);
        Date endTime = startTime == null || duration == null ? startTime : new Date(startTime.getTime() + duration);
        String stage = String.valueOf(row.get("stage"));
        String payloadRef = row.get("payload_ref") == null ? null : String.valueOf(row.get("payload_ref"));
        return new RagTraceNodeResp()
                .setTraceId(String.valueOf(row.get("trace_id")))
                .setNodeId(stage)
                .setNodeName(stage)
                .setNodeType(stage)
                .setStatus(row.get("status") == null ? null : String.valueOf(row.get("status")).toUpperCase())
                .setPayloadRef(payloadRef)
                .setDetails(parsePayloadDetails(stage, payloadRef))
                .setDurationMs(duration)
                .setStartTime(startTime)
                .setEndTime(endTime);
    }

    /**
     * 兼容新旧两类 trace payload：
     * 1. 新链路写入结构化 JSON，前端可直接按字段展示。
     * 2. 历史数据仍可能是 key=value,key2=value2 形式，这里尽量兜底解析。
     */
    private Map<String, Object> parsePayloadDetails(String stage, String payloadRef) {
        if (!StringUtils.hasText(payloadRef)) {
            return null;
        }
        String trimmedPayload = payloadRef.trim();
        if (trimmedPayload.startsWith("{") && trimmedPayload.endsWith("}")) {
            try {
                LinkedHashMap<String, Object> details = objectMapper.readValue(
                        trimmedPayload, new TypeReference<LinkedHashMap<String, Object>>() {
                        });
                return normalizeStageDetails(stage, details, trimmedPayload);
            } catch (Exception ignored) {
                // 历史截断数据可能不是完整 JSON，继续走 legacy 解析兜底。
            }
        }
        Map<String, Object> legacyDetails = parseLegacyDetails(trimmedPayload);
        if (!legacyDetails.isEmpty()) {
            return normalizeStageDetails(stage, legacyDetails, trimmedPayload);
        }
        return Map.of("rawPayload", trimmedPayload);
    }

    /**
     * 旧版 trace payload 是阶段各自定义的短字符串。
     * 这里把历史 key 尽量映射成当前前端使用的稳定字段名，避免详情页只能看到空卡片。
     */
    private Map<String, Object> normalizeStageDetails(String stage, Map<String, Object> originalDetails, String rawPayload) {
        if (originalDetails == null || originalDetails.isEmpty()) {
            return Map.of("rawPayload", rawPayload);
        }
        LinkedHashMap<String, Object> details = new LinkedHashMap<>(originalDetails);
        details.putIfAbsent("rawPayload", rawPayload);
        if ("REWRITE".equalsIgnoreCase(stage)) {
            renameIfMissing(details, "rewrittenQuery", "rewrittenQuestion");
            return details;
        }
        if ("INTENT".equalsIgnoreCase(stage)) {
            renameIfMissing(details, "intent", "resolvedIntentType");
            renameIfMissing(details, "fallback", "fallbackModel");
            if (!details.containsKey("routeSource")) {
                Object fallbackModel = details.get("fallbackModel");
                if (fallbackModel instanceof Boolean fallback) {
                    details.put("routeSource", fallback ? "MODEL_CLASSIFIER" : "LEAF_SCORE");
                }
            }
            return details;
        }
        if ("INTENT_SCORE".equalsIgnoreCase(stage)) {
            renameIfMissing(details, "matchCount", "matchedCount");
            renameIfMissing(details, "top", "topIntentCode");
            if (!details.containsKey("routedKnowledgeBaseIds")) {
                Object kbIds = details.get("kbIds");
                if (kbIds != null) {
                    details.put("routedKnowledgeBaseIds", parseListLikeValue(String.valueOf(kbIds)));
                }
            }
            if (!details.containsKey("matchedLeaves") && details.get("topIntentCode") != null) {
                details.put("matchedLeaves", List.of(Map.of("intentCode", details.get("topIntentCode"))));
            }
            return details;
        }
        if ("RETRIEVE".equalsIgnoreCase(stage)) {
            if (!details.containsKey("scope")) {
                if (Boolean.TRUE.equals(details.get("fallback")) || "global".equals(details.get("fallback"))) {
                    details.put("scope", "GLOBAL_FALLBACK");
                } else if (details.containsKey("kbIds")) {
                    details.put("scope", "KB_SCOPED");
                } else if (details.containsKey("global")) {
                    details.put("scope", "GLOBAL");
                }
            }
            if (!details.containsKey("knowledgeBaseIds") && details.get("kbIds") != null) {
                details.put("knowledgeBaseIds", parseListLikeValue(String.valueOf(details.get("kbIds"))));
            }
            return details;
        }
        if ("RERANK".equalsIgnoreCase(stage)) {
            return details;
        }
        if ("STREAM_CANDIDATE".equalsIgnoreCase(stage)) {
            renameIfMissing(details, "thinking", "thinkingProvided");
            if (!details.containsKey("deepThinking") && details.containsKey("thinkingProvided")) {
                details.put("deepThinking", Boolean.TRUE.equals(details.get("thinkingProvided")));
            }
            return details;
        }
        if ("GENERATE".equalsIgnoreCase(stage)) {
            if (!details.containsKey("answerSource")) {
                if ("STATIC_REFUSAL".equals(details.get("intent")) || "rerank-empty".equals(details.get("reason"))) {
                    details.put("answerSource", "STATIC_REFUSAL");
                    details.putIfAbsent("staticRefusal", true);
                } else if (details.containsKey("citationCount")) {
                    details.put("answerSource", "RAG_MODEL");
                } else if ("CHITCHAT".equals(details.get("intent"))) {
                    details.put("answerSource", "MODEL_DIRECT");
                } else if ("SYSTEM".equals(details.get("intent"))) {
                    details.put("answerSource", "SYSTEM_PROMPT");
                }
            }
            return details;
        }
        return details;
    }

    private void renameIfMissing(Map<String, Object> details, String sourceKey, String targetKey) {
        if (!details.containsKey(targetKey) && details.containsKey(sourceKey)) {
            details.put(targetKey, details.get(sourceKey));
        }
    }

    private List<String> parseListLikeValue(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return List.of();
        }
        String trimmed = rawValue.trim();
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) {
            return List.of(trimmed);
        }
        String content = trimmed.substring(1, trimmed.length() - 1).trim();
        if (!StringUtils.hasText(content)) {
            return List.of();
        }
        return List.of(content.split(","))
                .stream()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    private Map<String, Object> parseLegacyDetails(String payloadRef) {
        Map<String, Object> details = new LinkedHashMap<>();
        int bracketDepth = 0;
        StringBuilder token = new StringBuilder();
        for (int index = 0; index < payloadRef.length(); index++) {
            char current = payloadRef.charAt(index);
            if (current == '[' || current == '{' || current == '(') {
                bracketDepth++;
            } else if (current == ']' || current == '}' || current == ')') {
                bracketDepth = Math.max(0, bracketDepth - 1);
            }
            if (current == ',' && bracketDepth == 0) {
                appendLegacyEntry(details, token.toString());
                token.setLength(0);
                continue;
            }
            token.append(current);
        }
        appendLegacyEntry(details, token.toString());
        return details;
    }

    private void appendLegacyEntry(Map<String, Object> details, String token) {
        if (!StringUtils.hasText(token)) {
            return;
        }
        int separatorIndex = token.indexOf('=');
        if (separatorIndex < 0) {
            details.put("rawPayload", token.trim());
            return;
        }
        String key = token.substring(0, separatorIndex).trim();
        String rawValue = token.substring(separatorIndex + 1).trim();
        if (!StringUtils.hasText(key)) {
            return;
        }
        details.put(key, coerceLegacyValue(rawValue));
    }

    private Object coerceLegacyValue(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return rawValue;
        }
        if ("true".equalsIgnoreCase(rawValue) || "false".equalsIgnoreCase(rawValue)) {
            return Boolean.parseBoolean(rawValue);
        }
        try {
            if (rawValue.contains(".")) {
                return Double.parseDouble(rawValue);
            }
            return Long.parseLong(rawValue);
        } catch (NumberFormatException ignored) {
            return rawValue;
        }
    }

    private Long resolveNodeDuration(Object latencyValue, Date startTime, Date nextStartTime) {
        Long recorded = latencyValue == null ? null : ((Number) latencyValue).longValue();
        if (recorded != null && recorded > 0) {
            return recorded;
        }
        if (startTime != null && nextStartTime != null && nextStartTime.after(startTime)) {
            return Math.max(1L, nextStartTime.getTime() - startTime.getTime());
        }
        return startTime == null ? null : 1L;
    }
}
