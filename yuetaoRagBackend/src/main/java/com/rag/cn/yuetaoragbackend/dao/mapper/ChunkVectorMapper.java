package com.rag.cn.yuetaoragbackend.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rag.cn.yuetaoragbackend.dao.entity.ChunkVectorDO;
import com.rag.cn.yuetaoragbackend.dao.projection.RetrievedChunk;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @author zrq
 * 2026/04/22 14:50
 */
@Mapper
public interface ChunkVectorMapper extends BaseMapper<ChunkVectorDO> {

    List<RetrievedChunk> selectByVectorSearch(@Param("vectorLiteral") String vectorLiteral,
                                              @Param("admin") boolean admin,
                                              @Param("rankLevel") int rankLevel,
                                              @Param("departmentId") Long departmentId,
                                              @Param("hasKnowledgeBaseIds") boolean hasKnowledgeBaseIds,
                                              @Param("knowledgeBaseIds") List<Long> knowledgeBaseIds,
                                              @Param("recallLimit") int recallLimit);
}
