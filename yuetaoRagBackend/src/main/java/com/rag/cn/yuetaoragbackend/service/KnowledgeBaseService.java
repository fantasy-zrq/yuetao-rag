package com.rag.cn.yuetaoragbackend.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.rag.cn.yuetaoragbackend.dao.entity.KnowledgeBaseDO;
import com.rag.cn.yuetaoragbackend.dto.req.CreateKnowledgeBaseReq;
import com.rag.cn.yuetaoragbackend.dto.req.DeleteKnowledgeBaseReq;
import com.rag.cn.yuetaoragbackend.dto.req.UpdateKnowledgeBaseReq;
import com.rag.cn.yuetaoragbackend.dto.resp.KnowledgeBaseCreateResp;
import com.rag.cn.yuetaoragbackend.dto.resp.KnowledgeBaseDetailResp;
import com.rag.cn.yuetaoragbackend.dto.resp.KnowledgeBaseListResp;
import java.util.List;

/**
 * @author zrq
 * 2026/04/22 15:10
 */
public interface KnowledgeBaseService extends IService<KnowledgeBaseDO> {

    KnowledgeBaseCreateResp createKnowledgeBase(CreateKnowledgeBaseReq requestParam);

    KnowledgeBaseDetailResp updateKnowledgeBase(UpdateKnowledgeBaseReq requestParam);

    void deleteKnowledgeBase(DeleteKnowledgeBaseReq requestParam);

    List<KnowledgeBaseListResp> listKnowledgeBases();

    KnowledgeBaseDetailResp getKnowledgeBase(Long id);
}
