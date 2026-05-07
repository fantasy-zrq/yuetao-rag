package com.rag.cn.yuetaoragbackend.service.impl;

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
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * @author zrq
 * 2026/05/07
 */
@Service
@RequiredArgsConstructor
public class RagTraceServiceImpl implements RagTraceService {

    private final QaTraceLogMapper qaTraceLogMapper;

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
        return new RagTraceNodeResp()
                .setTraceId(String.valueOf(row.get("trace_id")))
                .setNodeId(String.valueOf(row.get("stage")))
                .setNodeName(String.valueOf(row.get("stage")))
                .setNodeType(String.valueOf(row.get("stage")))
                .setStatus(row.get("status") == null ? null : String.valueOf(row.get("status")).toUpperCase())
                .setDurationMs(duration)
                .setStartTime(startTime)
                .setEndTime(endTime);
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
