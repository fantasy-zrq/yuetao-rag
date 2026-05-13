package com.rag.cn.yuetaoragbackend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.cn.yuetaoragbackend.config.enums.DeleteFlagEnum;
import com.rag.cn.yuetaoragbackend.config.enums.IntentNodeEnabledEnum;
import com.rag.cn.yuetaoragbackend.dao.entity.IntentNodeDO;
import com.rag.cn.yuetaoragbackend.dao.mapper.IntentNodeMapper;
import com.rag.cn.yuetaoragbackend.dto.req.BatchIdsReq;
import com.rag.cn.yuetaoragbackend.dto.req.CreateIntentNodeReq;
import com.rag.cn.yuetaoragbackend.dto.req.UpdateIntentNodeReq;
import com.rag.cn.yuetaoragbackend.dto.resp.IntentNodeTreeResp;
import com.rag.cn.yuetaoragbackend.framework.context.UserContext;
import com.rag.cn.yuetaoragbackend.framework.exception.ClientException;
import com.rag.cn.yuetaoragbackend.framework.exception.intent.IntentCodeAlreadyExistsException;
import com.rag.cn.yuetaoragbackend.framework.exception.intent.IntentNodeHasChildrenException;
import com.rag.cn.yuetaoragbackend.framework.exception.intent.IntentNodeNotFoundException;
import com.rag.cn.yuetaoragbackend.service.IntentNodeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author zrq
 * 2026/05/11
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IntentNodeServiceImpl extends ServiceImpl<IntentNodeMapper, IntentNodeDO>
        implements IntentNodeService {

    private static final String REDIS_CACHE_KEY = "rag:intent-tree:all";
    private static final long CACHE_TTL_SECONDS = 3600;

    private final IntentNodeMapper intentNodeMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createIntentNode(CreateIntentNodeReq requestParam) {
        if (existsByIntentCode(requestParam.getIntentCode(), null)) {
            throw new IntentCodeAlreadyExistsException(requestParam.getIntentCode());
        }

        Long userId = currentUserId();
        Long parentId = 0L;
        String parentCode = null;
        int level = requestParam.getLevel() == null ? 0 : requestParam.getLevel();

        if (StringUtils.hasText(requestParam.getParentCode())) {
            IntentNodeDO parent = getActiveByIntentCodeOrThrow(requestParam.getParentCode());
            parentId = parent.getId();
            parentCode = parent.getIntentCode();
            level = parent.getLevel() + 1;
        }

        IntentNodeDO entity = new IntentNodeDO()
                .setIntentCode(requestParam.getIntentCode())
                .setName(requestParam.getName())
                .setLevel(level)
                .setParentId(parentId)
                .setParentCode(parentCode)
                .setDescription(requestParam.getDescription())
                .setExamples(serializeExamples(requestParam.getExamples()))
                .setCollectionName(requestParam.getCollectionName())
                .setKbId(requestParam.getKbId())
                .setMcpToolId(requestParam.getMcpToolId())
                .setTopK(requestParam.getTopK())
                .setKind(requestParam.getKind())
                .setSortOrder(requestParam.getSortOrder() == null ? 0 : requestParam.getSortOrder())
                .setEnabled(requestParam.getEnabled() == null ? IntentNodeEnabledEnum.ENABLED.getCode() : requestParam.getEnabled())
                .setPromptSnippet(requestParam.getPromptSnippet())
                .setPromptTemplate(requestParam.getPromptTemplate())
                .setParamPromptTemplate(requestParam.getParamPromptTemplate())
                .setCreatedBy(userId)
                .setUpdatedBy(userId);

        intentNodeMapper.insert(entity);
        invalidateCache();
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateIntentNode(Long id, UpdateIntentNodeReq requestParam) {
        IntentNodeDO existing = getActiveByIdOrThrow(id);

        Long userId = currentUserId();
        Long parentId = existing.getParentId();
        String parentCode = existing.getParentCode();
        int level = existing.getLevel();

        if (requestParam.getParentCode() != null) {
            if (StringUtils.hasText(requestParam.getParentCode())) {
                IntentNodeDO parent = getActiveByIntentCodeOrThrow(requestParam.getParentCode());
                if (parent.getId().equals(id)) {
                    throw new ClientException("节点不能成为自己的子节点");
                }
                if (isDescendantOf(parent.getId(), id)) {
                    throw new ClientException("不能将节点移动到其自身后代下，会形成环");
                }
                parentId = parent.getId();
                parentCode = parent.getIntentCode();
                level = parent.getLevel() + 1;
            } else {
                parentId = 0L;
                parentCode = null;
                level = 0;
            }
        }

        var updateWrapper = Wrappers.<IntentNodeDO>lambdaUpdate()
                .eq(IntentNodeDO::getId, id)
                .set(IntentNodeDO::getUpdatedBy, userId)
                .set(IntentNodeDO::getParentId, parentId)
                .set(IntentNodeDO::getParentCode, parentCode)
                .set(IntentNodeDO::getLevel, level);

        if (requestParam.getName() != null) updateWrapper.set(IntentNodeDO::getName, requestParam.getName());
        if (requestParam.hasDescription())
            updateWrapper.set(IntentNodeDO::getDescription, normalizeNullableText(requestParam.getDescription()));
        if (requestParam.hasExamples())
            updateWrapper.set(IntentNodeDO::getExamples, serializeExamples(requestParam.getExamples()));
        if (requestParam.hasCollectionName())
            updateWrapper.set(IntentNodeDO::getCollectionName, normalizeNullableText(requestParam.getCollectionName()));
        if (requestParam.hasKbId()) updateWrapper.set(IntentNodeDO::getKbId, requestParam.getKbId());
        if (requestParam.hasMcpToolId())
            updateWrapper.set(IntentNodeDO::getMcpToolId, normalizeNullableText(requestParam.getMcpToolId()));
        if (requestParam.hasTopK()) updateWrapper.set(IntentNodeDO::getTopK, requestParam.getTopK());
        if (requestParam.getKind() != null) updateWrapper.set(IntentNodeDO::getKind, requestParam.getKind());
        if (requestParam.getSortOrder() != null) updateWrapper.set(IntentNodeDO::getSortOrder, requestParam.getSortOrder());
        if (requestParam.getEnabled() != null) updateWrapper.set(IntentNodeDO::getEnabled, requestParam.getEnabled());
        if (requestParam.hasPromptSnippet())
            updateWrapper.set(IntentNodeDO::getPromptSnippet, normalizeNullableText(requestParam.getPromptSnippet()));
        if (requestParam.hasPromptTemplate())
            updateWrapper.set(IntentNodeDO::getPromptTemplate, normalizeNullableText(requestParam.getPromptTemplate()));
        if (requestParam.hasParamPromptTemplate()) {
            updateWrapper.set(IntentNodeDO::getParamPromptTemplate,
                    normalizeNullableText(requestParam.getParamPromptTemplate()));
        }

        intentNodeMapper.update(null, updateWrapper);
        invalidateCache();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteIntentNode(Long id) {
        getActiveByIdOrThrow(id);

        boolean hasChildren = intentNodeMapper.exists(Wrappers.<IntentNodeDO>lambdaQuery()
                .eq(IntentNodeDO::getParentId, id)
                .eq(IntentNodeDO::getDeleteFlag, DeleteFlagEnum.NORMAL.getCode()));
        if (hasChildren) {
            throw new IntentNodeHasChildrenException(id);
        }

        IntentNodeDO delete = new IntentNodeDO();
        delete.setId(id);
        delete.setDeleteFlag(DeleteFlagEnum.DELETED.getCode());
        intentNodeMapper.updateById(delete);
        invalidateCache();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchDeleteIntentNodes(BatchIdsReq requestParam) {
        List<Long> ids = requestParam.getIds();
        if (CollectionUtils.isEmpty(ids)) {
            return;
        }

        for (Long id : ids) {
            boolean hasChildren = intentNodeMapper.exists(Wrappers.<IntentNodeDO>lambdaQuery()
                    .eq(IntentNodeDO::getParentId, id)
                    .eq(IntentNodeDO::getDeleteFlag, DeleteFlagEnum.NORMAL.getCode()));
            if (hasChildren) {
                throw new IntentNodeHasChildrenException(id);
            }
        }

        intentNodeMapper.update(null, Wrappers.<IntentNodeDO>lambdaUpdate()
                .set(IntentNodeDO::getDeleteFlag, DeleteFlagEnum.DELETED.getCode())
                .in(IntentNodeDO::getId, ids)
                .eq(IntentNodeDO::getDeleteFlag, DeleteFlagEnum.NORMAL.getCode()));
        invalidateCache();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchEnableIntentNodes(BatchIdsReq requestParam) {
        List<Long> ids = requestParam.getIds();
        if (CollectionUtils.isEmpty(ids)) {
            return;
        }
        intentNodeMapper.update(null, Wrappers.<IntentNodeDO>lambdaUpdate()
                .set(IntentNodeDO::getEnabled, IntentNodeEnabledEnum.ENABLED.getCode())
                .in(IntentNodeDO::getId, ids)
                .eq(IntentNodeDO::getDeleteFlag, DeleteFlagEnum.NORMAL.getCode()));
        invalidateCache();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchDisableIntentNodes(BatchIdsReq requestParam) {
        List<Long> targetIds = requestParam.getIds();
        if (CollectionUtils.isEmpty(targetIds)) {
            return;
        }

        List<IntentNodeDO> allNodes = intentNodeMapper.selectList(
                Wrappers.<IntentNodeDO>lambdaQuery()
                        .eq(IntentNodeDO::getDeleteFlag, DeleteFlagEnum.NORMAL.getCode()));

        Map<Long, List<IntentNodeDO>> childrenMap = allNodes.stream()
                .collect(Collectors.groupingBy(IntentNodeDO::getParentId));

        Set<Long> allToDisable = new HashSet<>(targetIds);
        Deque<Long> queue = new ArrayDeque<>(targetIds);
        while (!queue.isEmpty()) {
            Long currentId = queue.poll();
            List<IntentNodeDO> children = childrenMap.getOrDefault(currentId, Collections.emptyList());
            for (IntentNodeDO child : children) {
                if (allToDisable.add(child.getId())) {
                    queue.add(child.getId());
                }
            }
        }

        intentNodeMapper.update(null, Wrappers.<IntentNodeDO>lambdaUpdate()
                .set(IntentNodeDO::getEnabled, IntentNodeEnabledEnum.DISABLED.getCode())
                .in(IntentNodeDO::getId, allToDisable)
                .eq(IntentNodeDO::getDeleteFlag, DeleteFlagEnum.NORMAL.getCode()));
        invalidateCache();
    }

    @Override
    public List<IntentNodeTreeResp> getTree() {
        String cached = stringRedisTemplate.opsForValue().get(REDIS_CACHE_KEY);
        if (cached != null) {
            try {
                List<IntentNodeTreeResp> tree = objectMapper.readValue(cached, new TypeReference<List<IntentNodeTreeResp>>() {
                });
                log.info("[INTENT] 意图树缓存命中: nodeCount={}", countNodes(tree));
                return tree;
            } catch (JsonProcessingException e) {
                log.warn("反序列化意图树缓存失败，回退查询数据库", e);
            }
        }

        List<IntentNodeDO> allNodes = intentNodeMapper.selectList(
                Wrappers.<IntentNodeDO>lambdaQuery()
                        .eq(IntentNodeDO::getDeleteFlag, DeleteFlagEnum.NORMAL.getCode()));

        List<IntentNodeTreeResp> tree = assembleTree(allNodes);
        log.info("[INTENT] 意图树缓存未命中，从数据库加载: nodeCount={}", allNodes.size());

        try {
            String json = objectMapper.writeValueAsString(tree);
            stringRedisTemplate.opsForValue().set(REDIS_CACHE_KEY, json, CACHE_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (JsonProcessingException e) {
            log.warn("序列化意图树缓存失败", e);
        }

        return tree;
    }

    private int countNodes(List<IntentNodeTreeResp> nodes) {
        if (nodes == null) return 0;
        int count = nodes.size();
        for (IntentNodeTreeResp node : nodes) {
            count += countNodes(node.getChildren());
        }
        return count;
    }

    // ==================== 内部方法 ====================

    private List<IntentNodeTreeResp> assembleTree(List<IntentNodeDO> allNodes) {
        Map<Long, List<IntentNodeDO>> childrenMap = allNodes.stream()
                .collect(Collectors.groupingBy(IntentNodeDO::getParentId));

        childrenMap.values().forEach(list ->
                list.sort(Comparator.comparingInt(IntentNodeDO::getSortOrder)
                        .thenComparingLong(IntentNodeDO::getId)));

        List<IntentNodeDO> roots = childrenMap.getOrDefault(0L, Collections.emptyList());
        return roots.stream()
                .map(root -> buildNodeTree(root, childrenMap))
                .toList();
    }

    private IntentNodeTreeResp buildNodeTree(IntentNodeDO node, Map<Long, List<IntentNodeDO>> childrenMap) {
        IntentNodeTreeResp resp = new IntentNodeTreeResp();
        BeanUtils.copyProperties(node, resp);

        List<IntentNodeDO> children = childrenMap.getOrDefault(node.getId(), Collections.emptyList());
        resp.setChildren(children.stream()
                .map(child -> buildNodeTree(child, childrenMap))
                .toList());
        return resp;
    }

    private boolean existsByIntentCode(String intentCode, Long excludeId) {
        LambdaQueryWrapper<IntentNodeDO> query = Wrappers.<IntentNodeDO>lambdaQuery()
                .eq(IntentNodeDO::getIntentCode, intentCode)
                .eq(IntentNodeDO::getDeleteFlag, DeleteFlagEnum.NORMAL.getCode());
        if (excludeId != null) {
            query.ne(IntentNodeDO::getId, excludeId);
        }
        return intentNodeMapper.exists(query);
    }

    private IntentNodeDO getActiveByIdOrThrow(Long id) {
        IntentNodeDO node = intentNodeMapper.selectOne(Wrappers.<IntentNodeDO>lambdaQuery()
                .eq(IntentNodeDO::getId, id)
                .eq(IntentNodeDO::getDeleteFlag, DeleteFlagEnum.NORMAL.getCode()));
        if (node == null) {
            throw new IntentNodeNotFoundException(id);
        }
        return node;
    }

    private IntentNodeDO getActiveByIntentCodeOrThrow(String intentCode) {
        IntentNodeDO node = intentNodeMapper.selectOne(Wrappers.<IntentNodeDO>lambdaQuery()
                .eq(IntentNodeDO::getIntentCode, intentCode)
                .eq(IntentNodeDO::getDeleteFlag, DeleteFlagEnum.NORMAL.getCode()));
        if (node == null) {
            throw new ClientException("父节点不存在或已删除：" + intentCode);
        }
        return node;
    }

    /**
     * 判断 candidateId 是否是 ancestorId 的后代（用于环检测）。
     */
    private boolean isDescendantOf(Long candidateId, Long ancestorId) {
        List<IntentNodeDO> allNodes = intentNodeMapper.selectList(
                Wrappers.<IntentNodeDO>lambdaQuery()
                        .eq(IntentNodeDO::getDeleteFlag, DeleteFlagEnum.NORMAL.getCode()));

        Map<Long, List<IntentNodeDO>> childrenMap = allNodes.stream()
                .collect(Collectors.groupingBy(IntentNodeDO::getParentId));

        Set<Long> visited = new HashSet<>();
        Deque<Long> queue = new ArrayDeque<>();
        queue.add(ancestorId);

        while (!queue.isEmpty()) {
            Long currentId = queue.poll();
            if (!visited.add(currentId)) {
                continue;
            }
            List<IntentNodeDO> children = childrenMap.getOrDefault(currentId, Collections.emptyList());
            for (IntentNodeDO child : children) {
                if (child.getId().equals(candidateId)) {
                    return true;
                }
                queue.add(child.getId());
            }
        }
        return false;
    }

    private String serializeExamples(String[] examples) {
        if (examples == null || examples.length == 0) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(examples);
        } catch (JsonProcessingException e) {
            log.warn("序列化 examples 失败", e);
            return null;
        }
    }

    private String normalizeNullableText(String value) {
        return StringUtils.hasText(value) ? value : null;
    }

    private void invalidateCache() {
        stringRedisTemplate.delete(REDIS_CACHE_KEY);
    }

    private Long currentUserId() {
        try {
            return Long.parseLong(UserContext.requireUser().getUserId());
        } catch (NumberFormatException ex) {
            throw new ClientException("当前登录用户ID非法");
        }
    }
}
