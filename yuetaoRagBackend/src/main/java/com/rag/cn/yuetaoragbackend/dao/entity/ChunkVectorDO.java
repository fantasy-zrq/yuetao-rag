package com.rag.cn.yuetaoragbackend.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.rag.cn.yuetaoragbackend.framework.database.JsonStringTypeHandler;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @author zrq
 * 2026/04/22 14:40
 */
@Data
@TableName(value = "t_chunk_vector", autoResultMap = true)
@Accessors(chain = true)
public class ChunkVectorDO {

    /**
     * 主键ID，使用雪花字符串。
     */
    @TableId(type = IdType.INPUT)
    private String id;

    /**
     * 切片文本内容。
     */
    private String content;

    /**
     * 元数据 JSONB。
     */
    @TableField(typeHandler = JsonStringTypeHandler.class)
    private String metadata;

    /**
     * pgvector 向量字段。
     */
    private String embedding;
}
