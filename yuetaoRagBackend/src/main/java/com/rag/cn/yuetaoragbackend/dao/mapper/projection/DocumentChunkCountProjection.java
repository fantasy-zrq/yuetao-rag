package com.rag.cn.yuetaoragbackend.dao.mapper.projection;

import lombok.Data;

@Data
public class DocumentChunkCountProjection {

    private Long documentId;

    private Long count;
}
