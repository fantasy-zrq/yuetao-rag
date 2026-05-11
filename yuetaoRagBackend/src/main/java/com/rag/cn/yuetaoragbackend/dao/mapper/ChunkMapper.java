package com.rag.cn.yuetaoragbackend.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rag.cn.yuetaoragbackend.dao.entity.ChunkDO;
import com.rag.cn.yuetaoragbackend.dao.mapper.projection.DocumentChunkCountProjection;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

/**
 * @author zrq
 * 2026/04/22 14:50
 */
@Mapper
public interface ChunkMapper extends BaseMapper<ChunkDO> {

    @Select("""
            <script>
            SELECT document_id AS documentId, COUNT(*) AS count
            FROM t_chunk
            WHERE delete_flag = 0
              AND document_id IN
              <foreach item='id' collection='documentIds' open='(' separator=',' close=')'>#{id}</foreach>
            GROUP BY document_id
            </script>
            """)
    List<DocumentChunkCountProjection> countByDocumentIds(@Param("documentIds") Collection<Long> documentIds);
}
