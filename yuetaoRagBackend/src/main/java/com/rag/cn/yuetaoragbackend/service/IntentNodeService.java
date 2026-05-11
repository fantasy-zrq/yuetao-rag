package com.rag.cn.yuetaoragbackend.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.rag.cn.yuetaoragbackend.dao.entity.IntentNodeDO;
import com.rag.cn.yuetaoragbackend.dto.req.BatchIdsReq;
import com.rag.cn.yuetaoragbackend.dto.req.CreateIntentNodeReq;
import com.rag.cn.yuetaoragbackend.dto.req.UpdateIntentNodeReq;
import com.rag.cn.yuetaoragbackend.dto.resp.IntentNodeTreeResp;

import java.util.List;

/**
 * @author zrq
 * 2026/05/11
 */
public interface IntentNodeService extends IService<IntentNodeDO> {

    Long createIntentNode(CreateIntentNodeReq requestParam);

    void updateIntentNode(Long id, UpdateIntentNodeReq requestParam);

    void deleteIntentNode(Long id);

    void batchDeleteIntentNodes(BatchIdsReq requestParam);

    void batchEnableIntentNodes(BatchIdsReq requestParam);

    void batchDisableIntentNodes(BatchIdsReq requestParam);

    List<IntentNodeTreeResp> getTree();
}
