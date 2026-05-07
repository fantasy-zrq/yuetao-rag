package com.rag.cn.yuetaoragbackend.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rag.cn.yuetaoragbackend.dao.entity.QaTraceLogDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * @author zrq
 * 2026/04/22 14:50
 */
@Mapper
public interface QaTraceLogMapper extends BaseMapper<QaTraceLogDO> {

    /**
     * 分页查询 trace runs（按 traceId 分组聚合）。
     */
    IPage<Map<String, Object>> selectPageRuns(Page<Map<String, Object>> page,
                                              @Param("traceIdPattern") String traceIdPattern);

    /**
     * 按 traceId 查询单条 run 详情（聚合）。
     */
    Map<String, Object> selectDetailByTraceId(@Param("traceId") String traceId);

    /**
     * 查询某个 trace 的所有节点（带 lead 窗口函数）。
     */
    List<Map<String, Object>> selectNodesByTraceId(@Param("traceId") String traceId);
}
