package com.rag.cn.yuetaoragbackend.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.cn.yuetaoragbackend.config.enums.DeleteFlagEnum;
import com.rag.cn.yuetaoragbackend.config.enums.IntentNodeEnabledEnum;
import com.rag.cn.yuetaoragbackend.dao.entity.IntentNodeDO;
import com.rag.cn.yuetaoragbackend.dao.mapper.IntentNodeMapper;
import com.rag.cn.yuetaoragbackend.framework.context.LoginUser;
import com.rag.cn.yuetaoragbackend.framework.context.UserContext;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * 意图树全链路集成测试。
 * 覆盖场景：创建 → 查询 → 更新 → 禁用 → 启用 → 删除 → 批量操作 → 缓存 → 校验 → 边界条件 → Bug 回归。
 */
@SpringBootTest(properties = {
        "app.ai.providers.bailian.api-key=test-bailian-key",
        "app.ai.embedding.default-model=text-embedding-v4",
        "app.ai.embedding.candidates[0].id=text-embedding-v4",
        "app.ai.embedding.candidates[0].provider=bailian",
        "app.ai.embedding.candidates[0].model=text-embedding-v4",
        "app.ai.embedding.candidates[0].dimension=1024",
        "spring.ai.vectorstore.pgvector.initialize-schema=false"
})
@AutoConfigureMockMvc
@TestMethodOrder(OrderAnnotation.class)
class IntentTreeControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private IntentNodeMapper intentNodeMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private final List<Long> nodeIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        UserContext.clear();
        nodeIds.forEach(id -> {
            try {
                intentNodeMapper.deleteById(id);
            } catch (Exception ignored) {
            }
        });
        nodeIds.clear();
        stringRedisTemplate.delete("rag:intent-tree:all");
    }

    // ==================== 1. 创建节点 ====================

    @Test
    @Order(1)
    void shouldCreateRootNode() throws Exception {
        UserContext.set(LoginUser.builder().userId("10001").build());

        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/intent-tree")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "intentCode": "DOMAIN_KB_TEST",
                                  "name": "测试知识库",
                                  "level": 0,
                                  "kind": 0,
                                  "description": "测试用DOMAIN节点",
                                  "collectionName": "test_collection",
                                  "sortOrder": 1,
                                  "enabled": 1
                                }
                                """))
                .andReturn();

        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode json = objectMapper.readTree(body);
        assertThat(json.path("code").asText()).isEqualTo("0");
        Long nodeId = json.path("data").asLong();
        assertThat(nodeId).isGreaterThan(0);
        nodeIds.add(nodeId);

        IntentNodeDO saved = intentNodeMapper.selectById(nodeId);
        assertThat(saved).isNotNull();
        assertThat(saved.getIntentCode()).isEqualTo("DOMAIN_KB_TEST");
        assertThat(saved.getName()).isEqualTo("测试知识库");
        assertThat(saved.getLevel()).isEqualTo(0);
        assertThat(saved.getParentId()).isEqualTo(0L);
        assertThat(saved.getParentCode()).isNull();
        assertThat(saved.getCreatedBy()).isEqualTo(10001L);
    }

    @Test
    @Order(2)
    void shouldCreateChildNode() throws Exception {
        UserContext.set(LoginUser.builder().userId("10001").build());
        Long parentId = createNodeDirectly("DOMAIN_KB_TEST2", "父节点", 0, null);

        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/intent-tree")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "intentCode": "CAT_RETURN_TEST",
                                  "name": "退换货",
                                  "level": 1,
                                  "parentCode": "DOMAIN_KB_TEST2",
                                  "kind": 0,
                                  "description": "退换货相关",
                                  "sortOrder": 1,
                                  "enabled": 1
                                }
                                """))
                .andReturn();

        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        Long childId = objectMapper.readTree(body).path("data").asLong();
        nodeIds.add(childId);

        IntentNodeDO child = intentNodeMapper.selectById(childId);
        assertThat(child.getParentId()).isEqualTo(parentId);
        assertThat(child.getParentCode()).isEqualTo("DOMAIN_KB_TEST2");
        assertThat(child.getLevel()).isEqualTo(1);
    }

    @Test
    @Order(3)
    void shouldRejectDuplicateIntentCode() throws Exception {
        UserContext.set(LoginUser.builder().userId("10001").build());
        createNodeDirectly("DUP_CODE_TEST", "唯一节点", 0, null);

        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/intent-tree")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "intentCode": "DUP_CODE_TEST",
                                  "name": "重复节点",
                                  "level": 0,
                                  "kind": 0,
                                  "enabled": 1
                                }
                                """))
                .andReturn();

        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).contains("已存在");
    }

    @Test
    @Order(4)
    void shouldCreateNodeWithAllOptionalFields() throws Exception {
        UserContext.set(LoginUser.builder().userId("10001").build());

        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/intent-tree")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "intentCode": "FULL_FIELDS_TEST",
                                  "name": "全字段节点",
                                  "level": 0,
                                  "kind": 0,
                                  "description": "包含所有可选字段",
                                  "collectionName": "full_collection",
                                  "topK": 5,
                                  "sortOrder": 3,
                                  "enabled": 1,
                                  "promptSnippet": "你是客服助手",
                                  "promptTemplate": "请根据以下信息回答：{context}",
                                  "examples": ["如何退货", "退款流程"]
                                }
                                """))
                .andReturn();

        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        Long nodeId = objectMapper.readTree(body).path("data").asLong();
        nodeIds.add(nodeId);

        IntentNodeDO saved = intentNodeMapper.selectById(nodeId);
        assertThat(saved.getCollectionName()).isEqualTo("full_collection");
        assertThat(saved.getTopK()).isEqualTo(5);
        assertThat(saved.getSortOrder()).isEqualTo(3);
        assertThat(saved.getPromptSnippet()).isEqualTo("你是客服助手");
        assertThat(saved.getPromptTemplate()).isEqualTo("请根据以下信息回答：{context}");
        assertThat(saved.getExamples()).contains("如何退货");
        assertThat(saved.getExamples()).contains("退款流程");
    }

    @Test
    @Order(5)
    void shouldCreateChildWithCorrectLevelInheritance() throws Exception {
        UserContext.set(LoginUser.builder().userId("10001").build());
        Long rootId = createNodeDirectly("LVL_ROOT", "根", 0, null);
        Long catId = createNodeDirectly("LVL_CAT", "分类", 1, "LVL_ROOT");

        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/intent-tree")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "intentCode": "LVL_TOPIC",
                                  "name": "主题",
                                  "level": 99,
                                  "parentCode": "LVL_CAT",
                                  "kind": 0,
                                  "enabled": 1
                                }
                                """))
                .andReturn();

        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        Long topicId = objectMapper.readTree(body).path("data").asLong();
        nodeIds.add(topicId);

        IntentNodeDO topic = intentNodeMapper.selectById(topicId);
        // level 应被服务层覆盖为 parent.level + 1 = 2，忽略请求中的 99
        assertThat(topic.getLevel()).isEqualTo(2);
        assertThat(topic.getParentId()).isEqualTo(catId);
    }

    @Test
    @Order(6)
    void shouldCreateGrandchildThreeLevelsDeep() throws Exception {
        UserContext.set(LoginUser.builder().userId("10001").build());
        Long rootId = createNodeDirectly("DEEP_ROOT", "深层根", 0, null);
        Long catId = createNodeDirectly("DEEP_CAT", "深层分类", 1, "DEEP_ROOT");
        Long topicId = createNodeDirectly("DEEP_TOPIC", "深层主题", 2, "DEEP_CAT");

        IntentNodeDO topic = intentNodeMapper.selectById(topicId);
        assertThat(topic.getParentId()).isEqualTo(catId);
        assertThat(topic.getLevel()).isEqualTo(2);

        // 验证三层树结构
        mockMvc.perform(MockMvcRequestBuilders.get("/intent-tree/trees")).andReturn();
        String cached = stringRedisTemplate.opsForValue().get("rag:intent-tree:all");
        assertThat(cached).isNotNull();
        assertThat(cached).contains("DEEP_ROOT");
        assertThat(cached).contains("DEEP_CAT");
        assertThat(cached).contains("DEEP_TOPIC");
    }

    @Test
    @Order(7)
    void shouldDefaultSortOrderAndEnabledWhenOmitted() throws Exception {
        UserContext.set(LoginUser.builder().userId("10001").build());

        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/intent-tree")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "intentCode": "DEFAULT_TEST",
                                  "name": "默认值节点",
                                  "level": 0,
                                  "kind": 0
                                }
                                """))
                .andReturn();

        Long nodeId = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").asLong();
        nodeIds.add(nodeId);

        IntentNodeDO saved = intentNodeMapper.selectById(nodeId);
        assertThat(saved.getSortOrder()).isEqualTo(0);
        assertThat(saved.getEnabled()).isEqualTo(IntentNodeEnabledEnum.ENABLED.getCode());
        assertThat(saved.getDeleteFlag()).isEqualTo(DeleteFlagEnum.NORMAL.getCode());
    }

    @Test
    @Order(8)
    void shouldRejectCreateWithNonExistentParentCode() throws Exception {
        UserContext.set(LoginUser.builder().userId("10001").build());

        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/intent-tree")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "intentCode": "ORPHAN_TEST",
                                  "name": "孤儿节点",
                                  "level": 1,
                                  "parentCode": "NON_EXISTENT_PARENT",
                                  "kind": 0,
                                  "enabled": 1
                                }
                                """))
                .andReturn();

        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).contains("父节点");
    }

    // ==================== 2. DTO 校验 ====================

    @Test
    @Order(10)
    void shouldRejectCreateWithBlankIntentCode() throws Exception {
        UserContext.set(LoginUser.builder().userId("10001").build());

        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/intent-tree")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "intentCode": "",
                                  "name": "测试",
                                  "level": 0,
                                  "kind": 0,
                                  "enabled": 1
                                }
                                """))
                .andReturn();

        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).contains("意图标识");
    }

    @Test
    @Order(11)
    void shouldRejectCreateWithBlankName() throws Exception {
        UserContext.set(LoginUser.builder().userId("10001").build());

        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/intent-tree")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "intentCode": "VALID_CODE",
                                  "name": "",
                                  "level": 0,
                                  "kind": 0,
                                  "enabled": 1
                                }
                                """))
                .andReturn();

        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).contains("节点名称");
    }

    @Test
    @Order(12)
    void shouldRejectCreateWithNullLevel() throws Exception {
        UserContext.set(LoginUser.builder().userId("10001").build());

        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/intent-tree")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "intentCode": "NO_LEVEL",
                                  "name": "无层级",
                                  "kind": 0,
                                  "enabled": 1
                                }
                                """))
                .andReturn();

        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).contains("层级");
    }

    @Test
    @Order(13)
    void shouldRejectBatchWithEmptyIds() throws Exception {
        UserContext.set(LoginUser.builder().userId("10001").build());

        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/intent-tree/batch/delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ids": []
                                }
                                """))
                .andReturn();

        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).contains("IDs");
    }

    // ==================== 3. 查询树 ====================

    @Test
    @Order(20)
    void shouldReturnTreeStructure() throws Exception {
        UserContext.set(LoginUser.builder().userId("10001").build());
        Long rootId = createNodeDirectly("TREE_ROOT", "根节点", 0, null);
        Long childId = createNodeDirectly("TREE_CHILD", "子节点", 1, "TREE_ROOT");

        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.get("/intent-tree/trees"))
                .andReturn();

        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(result.getResponse().getStatus()).isEqualTo(200);

        JsonNode root = objectMapper.readTree(body);
        JsonNode data = root.path("data");
        assertThat(data.isArray()).isTrue();

        JsonNode treeNode = null;
        for (JsonNode node : data) {
            if ("TREE_ROOT".equals(node.path("intentCode").asText())) {
                treeNode = node;
                break;
            }
        }
        assertThat(treeNode).isNotNull();
        assertThat(treeNode.path("children").isArray()).isTrue();
        assertThat(treeNode.path("children").size()).isEqualTo(1);
        assertThat(treeNode.path("children").get(0).path("intentCode").asText()).isEqualTo("TREE_CHILD");
    }

    @Test
    @Order(21)
    void shouldCacheTreeInRedis() throws Exception {
        UserContext.set(LoginUser.builder().userId("10001").build());
        createNodeDirectly("CACHE_ROOT", "缓存测试", 0, null);

        mockMvc.perform(MockMvcRequestBuilders.get("/intent-tree/trees")).andReturn();

        String cached = stringRedisTemplate.opsForValue().get("rag:intent-tree:all");
        assertThat(cached).isNotNull();
        assertThat(cached).contains("CACHE_ROOT");
    }

    @Test
    @Order(22)
    void shouldReturnEmptyArrayWhenNoNodes() throws Exception {
        UserContext.set(LoginUser.builder().userId("10001").build());

        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.get("/intent-tree/trees"))
                .andReturn();

        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode data = objectMapper.readTree(body).path("data");
        assertThat(data.isArray()).isTrue();
        // 可能有其他测试遗留的节点，但不应报错
    }

    @Test
    @Order(23)
    void shouldReturnSortedChildrenBySortOrder() throws Exception {
        UserContext.set(LoginUser.builder().userId("10001").build());
        Long rootId = createNodeDirectly("SORT_ROOT", "排序根", 0, null);
        createNodeDirectly("SORT_B", "B节点", 1, "SORT_ROOT").intValue();
        // 给 B 设置 sortOrder=2
        IntentNodeDO nodeB = intentNodeMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<IntentNodeDO>()
                        .eq(IntentNodeDO::getIntentCode, "SORT_B"));
        nodeB.setSortOrder(2);
        intentNodeMapper.updateById(nodeB);

        createNodeDirectly("SORT_A", "A节点", 1, "SORT_ROOT").intValue();
        IntentNodeDO nodeA = intentNodeMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<IntentNodeDO>()
                        .eq(IntentNodeDO::getIntentCode, "SORT_A"));
        nodeA.setSortOrder(1);
        intentNodeMapper.updateById(nodeA);

        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.get("/intent-tree/trees"))
                .andReturn();

        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode data = objectMapper.readTree(body).path("data");

        JsonNode sortRoot = null;
        for (JsonNode node : data) {
            if ("SORT_ROOT".equals(node.path("intentCode").asText())) {
                sortRoot = node;
                break;
            }
        }
        assertThat(sortRoot).isNotNull();
        JsonNode children = sortRoot.path("children");
        assertThat(children.size()).isEqualTo(2);
        // A(sortOrder=1) 应在 B(sortOrder=2) 前面
        assertThat(children.get(0).path("intentCode").asText()).isEqualTo("SORT_A");
        assertThat(children.get(1).path("intentCode").asText()).isEqualTo("SORT_B");
    }

    @Test
    @Order(24)
    void shouldNotIncludeDeletedNodesInTree() throws Exception {
        UserContext.set(LoginUser.builder().userId("10001").build());
        Long rootId = createNodeDirectly("DEL_TREE_ROOT", "删除树根", 0, null);
        Long childId = createNodeDirectly("DEL_TREE_CHILD", "删除树子", 1, "DEL_TREE_ROOT");

        // 先删除子节点
        mockMvc.perform(MockMvcRequestBuilders.delete("/intent-tree/" + childId)).andReturn();

        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.get("/intent-tree/trees"))
                .andReturn();

        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode data = objectMapper.readTree(body).path("data");

        JsonNode delRoot = null;
        for (JsonNode node : data) {
            if ("DEL_TREE_ROOT".equals(node.path("intentCode").asText())) {
                delRoot = node;
                break;
            }
        }
        assertThat(delRoot).isNotNull();
        // 子节点已被删除，不应出现在 children 中
        assertThat(delRoot.path("children").size()).isEqualTo(0);
    }

    // ==================== 4. 更新节点 ====================

    @Test
    @Order(30)
    void shouldUpdateNodeName() throws Exception {
        UserContext.set(LoginUser.builder().userId("10001").build());
        Long nodeId = createNodeDirectly("UPD_CODE", "原始名称", 0, null);

        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.put("/intent-tree/" + nodeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "更新后名称",
                                  "description": "更新后描述"
                                }
                                """))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        IntentNodeDO updated = intentNodeMapper.selectById(nodeId);
        assertThat(updated.getName()).isEqualTo("更新后名称");
        assertThat(updated.getDescription()).isEqualTo("更新后描述");
        assertThat(updated.getUpdatedBy()).isEqualTo(10001L);
    }

    @Test
    @Order(31)
    void shouldMoveNodeToNewParent() throws Exception {
        UserContext.set(LoginUser.builder().userId("10001").build());
        Long rootId = createNodeDirectly("MOVE_ROOT", "目标父节点", 0, null);
        Long nodeId = createNodeDirectly("MOVE_NODE", "待移动节点", 0, null);

        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.put("/intent-tree/" + nodeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "parentCode": "MOVE_ROOT"
                                }
                                """))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        IntentNodeDO moved = intentNodeMapper.selectById(nodeId);
        assertThat(moved.getParentId()).isEqualTo(rootId);
        assertThat(moved.getParentCode()).isEqualTo("MOVE_ROOT");
        assertThat(moved.getLevel()).isEqualTo(1);
    }

    @Test
    @Order(32)
    void shouldMoveNodeToRootWhenParentCodeBlank() throws Exception {
        UserContext.set(LoginUser.builder().userId("10001").build());
        Long rootId = createNodeDirectly("MV_ROOT_PARENT", "原父", 0, null);
        Long childId = createNodeDirectly("MV_TO_ROOT", "要回根", 1, "MV_ROOT_PARENT");

        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.put("/intent-tree/" + childId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "parentCode": ""
                                }
                                """))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        IntentNodeDO moved = intentNodeMapper.selectById(childId);
        assertThat(moved.getParentId()).isEqualTo(0L);
        assertThat(moved.getParentCode()).isNull();
        assertThat(moved.getLevel()).isEqualTo(0);
    }

    @Test
    @Order(33)
    void shouldPreserveUnchangedFieldsOnPartialUpdate() throws Exception {
        UserContext.set(LoginUser.builder().userId("10001").build());
        Long nodeId = createNodeDirectly("PARTIAL_UPD", "部分更新", 0, null);
        // 设置一些初始值
        IntentNodeDO node = intentNodeMapper.selectById(nodeId);
        node.setCollectionName("original_collection");
        node.setTopK(10);
        node.setSortOrder(5);
        intentNodeMapper.updateById(node);

        // 只更新 name
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.put("/intent-tree/" + nodeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "新名称"
                                }
                                """))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        IntentNodeDO updated = intentNodeMapper.selectById(nodeId);
        assertThat(updated.getName()).isEqualTo("新名称");
        // 其他字段应保持不变
        assertThat(updated.getCollectionName()).isEqualTo("original_collection");
        assertThat(updated.getTopK()).isEqualTo(10);
        assertThat(updated.getSortOrder()).isEqualTo(5);
    }

    @Test
    @Order(34)
    void shouldRejectSelfReferencingParentCode() throws Exception {
        UserContext.set(LoginUser.builder().userId("10001").build());
        Long nodeId = createNodeDirectly("SELF_REF", "自引用", 0, null);

        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.put("/intent-tree/" + nodeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "parentCode": "SELF_REF"
                                }
                                """))
                .andReturn();

        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).contains("自己的子节点");
    }

    @Test
    @Order(35)
    void shouldRejectCyclicParentCode() throws Exception {
        UserContext.set(LoginUser.builder().userId("10001").build());
        Long rootId = createNodeDirectly("CYCLE_ROOT", "环根", 0, null);
        Long childId = createNodeDirectly("CYCLE_CHILD", "环子", 1, "CYCLE_ROOT");
        Long grandchildId = createNodeDirectly("CYCLE_GC", "环孙", 2, "CYCLE_CHILD");

        // 尝试把根节点移到孙子下 → 会形成环
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.put("/intent-tree/" + rootId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "parentCode": "CYCLE_GC"
                                }
                                """))
                .andReturn();

        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).contains("环");
    }

    @Test
    @Order(36)
    void shouldRejectUpdateNonExistentNode() throws Exception {
        UserContext.set(LoginUser.builder().userId("10001").build());

        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.put("/intent-tree/999999999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "不存在"
                                }
                                """))
                .andReturn();

        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).contains("不存在");
    }

    @Test
    @Order(37)
    void shouldUpdateExamples() throws Exception {
        UserContext.set(LoginUser.builder().userId("10001").build());
        Long nodeId = createNodeDirectly("UPD_EXAMPLES", "示例更新", 0, null);

        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.put("/intent-tree/" + nodeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "examples": ["新问题1", "新问题2", "新问题3"]
                                }
                                """))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        IntentNodeDO updated = intentNodeMapper.selectById(nodeId);
        assertThat(updated.getExamples()).contains("新问题1");
        assertThat(updated.getExamples()).contains("新问题2");
        assertThat(updated.getExamples()).contains("新问题3");
    }

    @Test
    @Order(38)
    void shouldUpdateKindAndMcpFields() throws Exception {
        UserContext.set(LoginUser.builder().userId("10001").build());
        Long nodeId = createNodeDirectly("UPD_KIND", "类型变更", 0, null);

        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.put("/intent-tree/" + nodeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "kind": 2,
                                  "mcpToolId": "sales_query",
                                  "paramPromptTemplate": "提取参数"
                                }
                                """))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        IntentNodeDO updated = intentNodeMapper.selectById(nodeId);
        assertThat(updated.getKind()).isEqualTo(2);
        assertThat(updated.getMcpToolId()).isEqualTo("sales_query");
        assertThat(updated.getParamPromptTemplate()).isEqualTo("提取参数");
    }

    // ==================== 5. 禁用 / 启用 ====================

    @Test
    @Order(40)
    void shouldCascadeDisableChildren() throws Exception {
        UserContext.set(LoginUser.builder().userId("10001").build());
        Long rootId = createNodeDirectly("DIS_ROOT", "禁用父节点", 0, null);
        Long childId = createNodeDirectly("DIS_CHILD", "子节点", 1, "DIS_ROOT");
        Long grandchildId = createNodeDirectly("DIS_GC", "孙节点", 2, "DIS_CHILD");

        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/intent-tree/batch/disable")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ids": [%d]
                                }
                                """.formatted(rootId)))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);

        IntentNodeDO root = intentNodeMapper.selectById(rootId);
        IntentNodeDO child = intentNodeMapper.selectById(childId);
        IntentNodeDO grandchild = intentNodeMapper.selectById(grandchildId);
        assertThat(root.getEnabled()).isEqualTo(0);
        assertThat(child.getEnabled()).isEqualTo(0);
        assertThat(grandchild.getEnabled()).isEqualTo(0);
    }

    @Test
    @Order(41)
    void shouldEnableWithoutCascade() throws Exception {
        UserContext.set(LoginUser.builder().userId("10001").build());
        Long rootId = createNodeDirectly("EN_ROOT", "启用父节点", 0, null);
        Long childId = createNodeDirectly("EN_CHILD", "子节点", 1, "EN_ROOT");

        // 先全部禁用
        mockMvc.perform(MockMvcRequestBuilders.post("/intent-tree/batch/disable")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ids": [%d]
                                }
                                """.formatted(rootId)))
                .andReturn();

        // 只启用父节点，不级联
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/intent-tree/batch/enable")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ids": [%d]
                                }
                                """.formatted(rootId)))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);

        IntentNodeDO root = intentNodeMapper.selectById(rootId);
        IntentNodeDO child = intentNodeMapper.selectById(childId);
        assertThat(root.getEnabled()).isEqualTo(1);
        assertThat(child.getEnabled()).isEqualTo(0); // 子节点仍禁用
    }

    @Test
    @Order(42)
    void shouldCascadeDisableDeepTree() throws Exception {
        UserContext.set(LoginUser.builder().userId("10001").build());
        Long rootId = createNodeDirectly("DEEP_DIS_ROOT", "深层禁用根", 0, null);
        Long catId = createNodeDirectly("DEEP_DIS_CAT", "分类", 1, "DEEP_DIS_ROOT");
        Long topicId = createNodeDirectly("DEEP_DIS_TOPIC", "主题", 2, "DEEP_DIS_CAT");
        // 额外加一个同级分类，不应被禁用
        Long siblingId = createNodeDirectly("DEEP_DIS_SIB", "兄弟分类", 1, "DEEP_DIS_ROOT");

        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/intent-tree/batch/disable")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ids": [%d]
                                }
                                """.formatted(catId)))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);

        assertThat(intentNodeMapper.selectById(rootId).getEnabled()).isEqualTo(1); // 根不受影响
        assertThat(intentNodeMapper.selectById(catId).getEnabled()).isEqualTo(0); // 目标
        assertThat(intentNodeMapper.selectById(topicId).getEnabled()).isEqualTo(0); // 孙节点级联
        assertThat(intentNodeMapper.selectById(siblingId).getEnabled()).isEqualTo(1); // 兄弟不受影响
    }

    @Test
    @Order(43)
    void shouldBatchEnableMultipleNodes() throws Exception {
        UserContext.set(LoginUser.builder().userId("10001").build());
        Long id1 = createNodeDirectly("BEN_1", "批量启用1", 0, null);
        Long id2 = createNodeDirectly("BEN_2", "批量启用2", 0, null);

        // 先禁用
        mockMvc.perform(MockMvcRequestBuilders.post("/intent-tree/batch/disable")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ids": [%d, %d]
                                }
                                """.formatted(id1, id2)))
                .andReturn();

        assertThat(intentNodeMapper.selectById(id1).getEnabled()).isEqualTo(0);
        assertThat(intentNodeMapper.selectById(id2).getEnabled()).isEqualTo(0);

        // 批量启用
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/intent-tree/batch/enable")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ids": [%d, %d]
                                }
                                """.formatted(id1, id2)))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(intentNodeMapper.selectById(id1).getEnabled()).isEqualTo(1);
        assertThat(intentNodeMapper.selectById(id2).getEnabled()).isEqualTo(1);
    }

    @Test
    @Order(44)
    void shouldInvalidateCacheOnBatchDisable() throws Exception {
        UserContext.set(LoginUser.builder().userId("10001").build());
        Long nodeId = createNodeDirectly("CACHE_DIS", "缓存禁用", 0, null);

        // 写入缓存
        mockMvc.perform(MockMvcRequestBuilders.get("/intent-tree/trees")).andReturn();
        assertThat(stringRedisTemplate.hasKey("rag:intent-tree:all")).isTrue();

        // 批量禁用应清除缓存
        mockMvc.perform(MockMvcRequestBuilders.post("/intent-tree/batch/disable")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ids": [%d]
                                }
                                """.formatted(nodeId)))
                .andReturn();

        assertThat(stringRedisTemplate.hasKey("rag:intent-tree:all")).isFalse();
    }

    @Test
    @Order(45)
    void shouldInvalidateCacheOnBatchEnable() throws Exception {
        UserContext.set(LoginUser.builder().userId("10001").build());
        Long nodeId = createNodeDirectly("CACHE_EN", "缓存启用", 0, null);

        // 先禁用
        mockMvc.perform(MockMvcRequestBuilders.post("/intent-tree/batch/disable")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ids": [%d]
                                }
                                """.formatted(nodeId)))
                .andReturn();

        // 写入缓存
        mockMvc.perform(MockMvcRequestBuilders.get("/intent-tree/trees")).andReturn();
        assertThat(stringRedisTemplate.hasKey("rag:intent-tree:all")).isTrue();

        // 批量启用应清除缓存
        mockMvc.perform(MockMvcRequestBuilders.post("/intent-tree/batch/enable")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ids": [%d]
                                }
                                """.formatted(nodeId)))
                .andReturn();

        assertThat(stringRedisTemplate.hasKey("rag:intent-tree:all")).isFalse();
    }

    // ==================== 6. 删除 ====================

    @Test
    @Order(50)
    void shouldDeleteLeafNode() throws Exception {
        UserContext.set(LoginUser.builder().userId("10001").build());
        Long nodeId = createNodeDirectly("DEL_LEAF", "叶子节点", 0, null);

        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.delete("/intent-tree/" + nodeId))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        IntentNodeDO deleted = intentNodeMapper.selectById(nodeId);
        assertThat(deleted.getDeleteFlag()).isEqualTo(DeleteFlagEnum.DELETED.getCode());
    }

    @Test
    @Order(51)
    void shouldRejectDeleteWhenHasChildren() throws Exception {
        UserContext.set(LoginUser.builder().userId("10001").build());
        Long parentId = createNodeDirectly("DEL_PARENT", "有子节点", 0, null);
        Long childId = createNodeDirectly("DEL_CHILD2", "子节点", 1, "DEL_PARENT");

        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.delete("/intent-tree/" + parentId))
                .andReturn();

        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).contains("子节点");

        IntentNodeDO parent = intentNodeMapper.selectById(parentId);
        assertThat(parent.getDeleteFlag()).isEqualTo(DeleteFlagEnum.NORMAL.getCode());
    }

    @Test
    @Order(52)
    void shouldRejectDeleteNonExistentNode() throws Exception {
        UserContext.set(LoginUser.builder().userId("10001").build());

        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.delete("/intent-tree/999999999"))
                .andReturn();

        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).contains("不存在");
    }

    @Test
    @Order(53)
    void shouldDeleteNodeAfterChildrenDeleted() throws Exception {
        UserContext.set(LoginUser.builder().userId("10001").build());
        Long parentId = createNodeDirectly("DEL_AFTER_CHILD", "先删子再删父", 0, null);
        Long childId = createNodeDirectly("DEL_AFTER_CHILD_C", "子", 1, "DEL_AFTER_CHILD");

        // 先删子节点
        MvcResult delChild = mockMvc.perform(MockMvcRequestBuilders.delete("/intent-tree/" + childId))
                .andReturn();
        assertThat(delChild.getResponse().getStatus()).isEqualTo(200);

        // 再删父节点应成功
        MvcResult delParent = mockMvc.perform(MockMvcRequestBuilders.delete("/intent-tree/" + parentId))
                .andReturn();
        assertThat(delParent.getResponse().getStatus()).isEqualTo(200);

        IntentNodeDO parent = intentNodeMapper.selectById(parentId);
        assertThat(parent.getDeleteFlag()).isEqualTo(DeleteFlagEnum.DELETED.getCode());
    }

    @Test
    @Order(54)
    void shouldInvalidateCacheOnDelete() throws Exception {
        UserContext.set(LoginUser.builder().userId("10001").build());
        Long nodeId = createNodeDirectly("CACHE_DEL", "缓存删除", 0, null);

        // 写入缓存
        mockMvc.perform(MockMvcRequestBuilders.get("/intent-tree/trees")).andReturn();
        assertThat(stringRedisTemplate.hasKey("rag:intent-tree:all")).isTrue();

        // 删除应清除缓存
        mockMvc.perform(MockMvcRequestBuilders.delete("/intent-tree/" + nodeId)).andReturn();
        assertThat(stringRedisTemplate.hasKey("rag:intent-tree:all")).isFalse();
    }

    // ==================== 7. 批量删除 ====================

    @Test
    @Order(60)
    void shouldBatchDeleteNodes() throws Exception {
        UserContext.set(LoginUser.builder().userId("10001").build());
        Long id1 = createNodeDirectly("BD_ROOT1", "批量删除1", 0, null);
        Long id2 = createNodeDirectly("BD_ROOT2", "批量删除2", 0, null);

        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/intent-tree/batch/delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ids": [%d, %d]
                                }
                                """.formatted(id1, id2)))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);

        IntentNodeDO node1 = intentNodeMapper.selectById(id1);
        IntentNodeDO node2 = intentNodeMapper.selectById(id2);
        assertThat(node1.getDeleteFlag()).isEqualTo(DeleteFlagEnum.DELETED.getCode());
        assertThat(node2.getDeleteFlag()).isEqualTo(DeleteFlagEnum.DELETED.getCode());
    }

    @Test
    @Order(61)
    void shouldAbortBatchDeleteWhenAnyNodeHasChildren() throws Exception {
        UserContext.set(LoginUser.builder().userId("10001").build());
        Long leafId = createNodeDirectly("BD_LEAF", "叶子", 0, null);
        Long parentId = createNodeDirectly("BD_PARENT", "有子", 0, null);
        Long childId = createNodeDirectly("BD_PARENT_CHILD", "子", 1, "BD_PARENT");

        // 尝试批量删除叶子+有子节点的父节点 → 应失败
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/intent-tree/batch/delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ids": [%d, %d]
                                }
                                """.formatted(leafId, parentId)))
                .andReturn();

        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).contains("子节点");

        // 两个节点都不应被删除（事务回滚）
        assertThat(intentNodeMapper.selectById(leafId).getDeleteFlag())
                .isEqualTo(DeleteFlagEnum.NORMAL.getCode());
        assertThat(intentNodeMapper.selectById(parentId).getDeleteFlag())
                .isEqualTo(DeleteFlagEnum.NORMAL.getCode());
    }

    @Test
    @Order(62)
    void shouldInvalidateCacheOnBatchDelete() throws Exception {
        UserContext.set(LoginUser.builder().userId("10001").build());
        Long nodeId = createNodeDirectly("CACHE_BD", "缓存批量删除", 0, null);

        // 写入缓存
        mockMvc.perform(MockMvcRequestBuilders.get("/intent-tree/trees")).andReturn();
        assertThat(stringRedisTemplate.hasKey("rag:intent-tree:all")).isTrue();

        // 批量删除应清除缓存
        mockMvc.perform(MockMvcRequestBuilders.post("/intent-tree/batch/delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ids": [%d]
                                }
                                """.formatted(nodeId)))
                .andReturn();

        assertThat(stringRedisTemplate.hasKey("rag:intent-tree:all")).isFalse();
    }

    // ==================== 8. 缓存失效 ====================

    @Test
    @Order(70)
    void shouldInvalidateCacheOnWrite() throws Exception {
        UserContext.set(LoginUser.builder().userId("10001").build());
        Long nodeId = createNodeDirectly("CACHE_INV", "缓存失效测试", 0, null);

        // 先查询写入缓存
        mockMvc.perform(MockMvcRequestBuilders.get("/intent-tree/trees")).andReturn();
        assertThat(stringRedisTemplate.hasKey("rag:intent-tree:all")).isTrue();

        // 更新节点，应清除缓存
        UserContext.set(LoginUser.builder().userId("10001").build());
        MvcResult updateResult = mockMvc.perform(MockMvcRequestBuilders.put("/intent-tree/" + nodeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "更新后"
                                }
                                """))
                .andReturn();

        assertThat(updateResult.getResponse().getStatus()).isEqualTo(200);
        String updateBody = updateResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(updateBody).contains("\"code\":\"0\"");

        assertThat(stringRedisTemplate.hasKey("rag:intent-tree:all")).isFalse();
    }

    @Test
    @Order(71)
    void shouldInvalidateCacheOnCreate() throws Exception {
        UserContext.set(LoginUser.builder().userId("10001").build());

        // 写入缓存
        mockMvc.perform(MockMvcRequestBuilders.get("/intent-tree/trees")).andReturn();
        assertThat(stringRedisTemplate.hasKey("rag:intent-tree:all")).isTrue();

        // 创建新节点应清除缓存（interceptor 清除了 UserContext，需重新设置）
        UserContext.set(LoginUser.builder().userId("10001").build());
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/intent-tree")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "intentCode": "CACHE_CREATE_TEST",
                                  "name": "缓存创建",
                                  "level": 0,
                                  "kind": 0,
                                  "enabled": 1
                                }
                                """))
                .andReturn();

        Long nodeId = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").asLong();
        nodeIds.add(nodeId);

        assertThat(stringRedisTemplate.hasKey("rag:intent-tree:all")).isFalse();
    }

    // ==================== 9. 排序和树结构验证 ====================

    @Test
    @Order(80)
    void shouldSortBySortOrderThenId() throws Exception {
        UserContext.set(LoginUser.builder().userId("10001").build());
        Long rootId = createNodeDirectly("SORTED_ROOT", "排序根", 0, null);

        // 创建3个子节点，sortOrder 相同但 ID 不同
        Long id1 = createNodeDirectly("SORTED_A", "A", 1, "SORTED_ROOT");
        Long id2 = createNodeDirectly("SORTED_B", "B", 1, "SORTED_ROOT");
        Long id3 = createNodeDirectly("SORTED_C", "C", 1, "SORTED_ROOT");

        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.get("/intent-tree/trees"))
                .andReturn();

        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode data = objectMapper.readTree(body).path("data");

        JsonNode sortedRoot = null;
        for (JsonNode node : data) {
            if ("SORTED_ROOT".equals(node.path("intentCode").asText())) {
                sortedRoot = node;
                break;
            }
        }
        assertThat(sortedRoot).isNotNull();
        JsonNode children = sortedRoot.path("children");
        assertThat(children.size()).isEqualTo(3);
        // sortOrder 都是 0，按 id ASC 排序
        assertThat(children.get(0).path("intentCode").asText()).isEqualTo("SORTED_A");
        assertThat(children.get(1).path("intentCode").asText()).isEqualTo("SORTED_B");
        assertThat(children.get(2).path("intentCode").asText()).isEqualTo("SORTED_C");
    }

    @Test
    @Order(81)
    void shouldBuildCorrectTreeWithMultipleBranches() throws Exception {
        UserContext.set(LoginUser.builder().userId("10001").build());
        Long rootId = createNodeDirectly("MULTI_ROOT", "多分支根", 0, null);
        Long cat1 = createNodeDirectly("MULTI_CAT1", "分类1", 1, "MULTI_ROOT");
        Long cat2 = createNodeDirectly("MULTI_CAT2", "分类2", 1, "MULTI_ROOT");
        Long topic1 = createNodeDirectly("MULTI_T1", "主题1", 2, "MULTI_CAT1");
        Long topic2 = createNodeDirectly("MULTI_T2", "主题2", 2, "MULTI_CAT1");
        Long topic3 = createNodeDirectly("MULTI_T3", "主题3", 2, "MULTI_CAT2");

        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.get("/intent-tree/trees"))
                .andReturn();

        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode data = objectMapper.readTree(body).path("data");

        JsonNode multiRoot = null;
        for (JsonNode node : data) {
            if ("MULTI_ROOT".equals(node.path("intentCode").asText())) {
                multiRoot = node;
                break;
            }
        }
        assertThat(multiRoot).isNotNull();
        assertThat(multiRoot.path("children").size()).isEqualTo(2);

        JsonNode branch1 = multiRoot.path("children").get(0);
        assertThat(branch1.path("intentCode").asText()).isEqualTo("MULTI_CAT1");
        assertThat(branch1.path("children").size()).isEqualTo(2);

        JsonNode branch2 = multiRoot.path("children").get(1);
        assertThat(branch2.path("intentCode").asText()).isEqualTo("MULTI_CAT2");
        assertThat(branch2.path("children").size()).isEqualTo(1);
        assertThat(branch2.path("children").get(0).path("intentCode").asText()).isEqualTo("MULTI_T3");
    }

    // ==================== 10. Bug 回归测试 ====================

    @Test
    @Order(90)
    void shouldNotReturnDeletedNodesInTreeAfterBatchDelete() throws Exception {
        // 回归：批量删除后查询树不应包含已删除节点
        UserContext.set(LoginUser.builder().userId("10001").build());
        Long id1 = createNodeDirectly("REGRESSION_DEL1", "回归删除1", 0, null);
        Long id2 = createNodeDirectly("REGRESSION_DEL2", "回归删除2", 0, null);

        mockMvc.perform(MockMvcRequestBuilders.post("/intent-tree/batch/delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ids": [%d, %d]
                                }
                                """.formatted(id1, id2)))
                .andReturn();

        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.get("/intent-tree/trees"))
                .andReturn();

        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).doesNotContain("REGRESSION_DEL1");
        assertThat(body).doesNotContain("REGRESSION_DEL2");
    }

    @Test
    @Order(91)
    void shouldHandleConcurrentCacheInvalidation() throws Exception {
        // 回归：多次快速写操作后缓存应保持一致
        UserContext.set(LoginUser.builder().userId("10001").build());
        Long nodeId = createNodeDirectly("CONCURRENT_TEST", "并发测试", 0, null);

        // 快速执行多次更新
        for (int i = 0; i < 3; i++) {
            UserContext.set(LoginUser.builder().userId("10001").build());
            mockMvc.perform(MockMvcRequestBuilders.put("/intent-tree/" + nodeId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "name": "更新%d"
                                    }
                                    """.formatted(i)))
                    .andReturn();
        }

        // 查询应返回最新数据
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.get("/intent-tree/trees"))
                .andReturn();

        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).contains("更新2"); // 最后一次更新的名称
    }

    @Test
    @Order(92)
    void shouldNotAllowDeletedNodeToBeMoved() throws Exception {
        // 回归：已删除的节点不应能被更新
        UserContext.set(LoginUser.builder().userId("10001").build());
        Long nodeId = createNodeDirectly("DELETED_MOVE", "已删除移动", 0, null);

        // 先删除
        mockMvc.perform(MockMvcRequestBuilders.delete("/intent-tree/" + nodeId)).andReturn();

        // 尝试更新已删除节点
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.put("/intent-tree/" + nodeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "新名称"
                                }
                                """))
                .andReturn();

        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).contains("不存在");
    }

    @Test
    @Order(93)
    void shouldNotAllowDeletedNodeToBeDeletedAgain() throws Exception {
        // 回归：重复删除应返回不存在
        UserContext.set(LoginUser.builder().userId("10001").build());
        Long nodeId = createNodeDirectly("DOUBLE_DEL", "重复删除", 0, null);

        // 第一次删除
        MvcResult first = mockMvc.perform(MockMvcRequestBuilders.delete("/intent-tree/" + nodeId))
                .andReturn();
        assertThat(first.getResponse().getStatus()).isEqualTo(200);

        // 第二次删除应失败
        MvcResult second = mockMvc.perform(MockMvcRequestBuilders.delete("/intent-tree/" + nodeId))
                .andReturn();
        String body = second.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).contains("不存在");
    }

    @Test
    @Order(94)
    void shouldNotAllowBatchDisableDeletedNode() throws Exception {
        // 回归：批量禁用已删除节点应无效果（WHERE deleteFlag = NORMAL）
        UserContext.set(LoginUser.builder().userId("10001").build());
        Long nodeId = createNodeDirectly("BDIS_DEL", "已删除禁用", 0, null);

        // 先删除
        mockMvc.perform(MockMvcRequestBuilders.delete("/intent-tree/" + nodeId)).andReturn();

        // 批量禁用已删除节点
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/intent-tree/batch/disable")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ids": [%d]
                                }
                                """.formatted(nodeId)))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        // 已删除节点的 enabled 不应改变（deleteFlag=1 时 UPDATE WHERE deleteFlag=NORMAL 不匹配）
    }

    @Test
    @Order(95)
    void shouldHandleDuplicateIntentCodeAfterDeletion() throws Exception {
        // 回归：删除后同一 intentCode 应可重新创建（partial unique index WHERE delete_flag=0）
        UserContext.set(LoginUser.builder().userId("10001").build());
        Long nodeId = createNodeDirectly("REUSE_CODE", "可重用标识", 0, null);

        // 删除
        mockMvc.perform(MockMvcRequestBuilders.delete("/intent-tree/" + nodeId)).andReturn();

        // 用相同 intentCode 重新创建应成功（interceptor 清除了 UserContext，需重新设置）
        UserContext.set(LoginUser.builder().userId("10001").build());
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/intent-tree")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "intentCode": "REUSE_CODE",
                                  "name": "重用标识",
                                  "level": 0,
                                  "kind": 0,
                                  "enabled": 1
                                }
                                """))
                .andReturn();

        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode json = objectMapper.readTree(body);
        assertThat(json.path("code").asText()).isEqualTo("0");
        Long newId = json.path("data").asLong();
        assertThat(newId).isNotEqualTo(nodeId);
        nodeIds.add(newId);
    }

    @Test
    @Order(96)
    void shouldNotReturnDisabledNodesAsDifferent() throws Exception {
        // 回归：禁用节点在树中应保持结构，只是 enabled=0
        UserContext.set(LoginUser.builder().userId("10001").build());
        Long rootId = createNodeDirectly("DIS_TREE_ROOT", "禁用树根", 0, null);
        Long childId = createNodeDirectly("DIS_TREE_CHILD", "禁用树子", 1, "DIS_TREE_ROOT");

        // 禁用子节点
        mockMvc.perform(MockMvcRequestBuilders.post("/intent-tree/batch/disable")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ids": [%d]
                                }
                                """.formatted(childId)))
                .andReturn();

        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.get("/intent-tree/trees"))
                .andReturn();

        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode data = objectMapper.readTree(body).path("data");

        JsonNode disRoot = null;
        for (JsonNode node : data) {
            if ("DIS_TREE_ROOT".equals(node.path("intentCode").asText())) {
                disRoot = node;
                break;
            }
        }
        assertThat(disRoot).isNotNull();
        // 禁用的子节点仍应在树中（不会被过滤掉）
        assertThat(disRoot.path("children").size()).isEqualTo(1);
        assertThat(disRoot.path("children").get(0).path("enabled").asInt()).isEqualTo(0);
    }

    // ==================== 11. 复杂场景 ====================

    @Test
    @Order(100)
    void shouldHandleFullLifecycleCreateMoveDisableDelete() throws Exception {
        // 完整生命周期：创建 → 移动 → 禁用 → 启用 → 删除
        UserContext.set(LoginUser.builder().userId("10001").build());
        Long root1 = createNodeDirectly("LIFECYCLE_ROOT1", "根1", 0, null);
        Long root2 = createNodeDirectly("LIFECYCLE_ROOT2", "根2", 0, null);
        Long child = createNodeDirectly("LIFECYCLE_CHILD", "子", 0, null);

        // 1. 移动 child 到 root1 下
        UserContext.set(LoginUser.builder().userId("10001").build());
        MvcResult moveResult = mockMvc.perform(MockMvcRequestBuilders.put("/intent-tree/" + child)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "parentCode": "LIFECYCLE_ROOT1"
                                }
                                """))
                .andReturn();
        assertThat(moveResult.getResponse().getStatus()).isEqualTo(200);

        IntentNodeDO moved = intentNodeMapper.selectById(child);
        assertThat(moved.getParentId()).isEqualTo(root1);
        assertThat(moved.getLevel()).isEqualTo(1);

        // 2. 禁用 root1（应级联禁用 child）
        UserContext.set(LoginUser.builder().userId("10001").build());
        mockMvc.perform(MockMvcRequestBuilders.post("/intent-tree/batch/disable")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ids": [%d]
                                }
                                """.formatted(root1)))
                .andReturn();

        assertThat(intentNodeMapper.selectById(root1).getEnabled()).isEqualTo(0);
        assertThat(intentNodeMapper.selectById(child).getEnabled()).isEqualTo(0);

        // 3. 启用 root1（不级联）
        UserContext.set(LoginUser.builder().userId("10001").build());
        mockMvc.perform(MockMvcRequestBuilders.post("/intent-tree/batch/enable")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ids": [%d]
                                }
                                """.formatted(root1)))
                .andReturn();

        assertThat(intentNodeMapper.selectById(root1).getEnabled()).isEqualTo(1);
        assertThat(intentNodeMapper.selectById(child).getEnabled()).isEqualTo(0); // 仍禁用

        // 4. 先删子节点再删父节点
        UserContext.set(LoginUser.builder().userId("10001").build());
        mockMvc.perform(MockMvcRequestBuilders.delete("/intent-tree/" + child)).andReturn();
        mockMvc.perform(MockMvcRequestBuilders.delete("/intent-tree/" + root1)).andReturn();

        assertThat(intentNodeMapper.selectById(child).getDeleteFlag())
                .isEqualTo(DeleteFlagEnum.DELETED.getCode());
        assertThat(intentNodeMapper.selectById(root1).getDeleteFlag())
                .isEqualTo(DeleteFlagEnum.DELETED.getCode());
    }

    @Test
    @Order(101)
    void shouldHandleMoveBetweenBranches() throws Exception {
        // 跨分支移动：从一个父节点移到另一个父节点
        UserContext.set(LoginUser.builder().userId("10001").build());
        Long rootA = createNodeDirectly("BRANCH_A", "分支A", 0, null);
        Long rootB = createNodeDirectly("BRANCH_B", "分支B", 0, null);
        Long child = createNodeDirectly("BRANCH_CHILD", "可移动子", 1, "BRANCH_A");

        // 验证初始位置
        assertThat(intentNodeMapper.selectById(child).getParentId()).isEqualTo(rootA);
        assertThat(intentNodeMapper.selectById(child).getLevel()).isEqualTo(1);

        // 移动到分支B
        UserContext.set(LoginUser.builder().userId("10001").build());
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.put("/intent-tree/" + child)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "parentCode": "BRANCH_B"
                                }
                                """))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        IntentNodeDO moved = intentNodeMapper.selectById(child);
        assertThat(moved.getParentId()).isEqualTo(rootB);
        assertThat(moved.getParentCode()).isEqualTo("BRANCH_B");
        assertThat(moved.getLevel()).isEqualTo(1);
    }

    // ==================== 辅助方法 ====================

    private Long createNodeDirectly(String intentCode, String name, int level, String parentCode) {
        IntentNodeDO node = new IntentNodeDO()
                .setIntentCode(intentCode)
                .setName(name)
                .setLevel(level)
                .setParentId(0L)
                .setParentCode(parentCode)
                .setKind(0)
                .setSortOrder(0)
                .setEnabled(IntentNodeEnabledEnum.ENABLED.getCode())
                .setCreatedBy(10001L)
                .setUpdatedBy(10001L);

        if (parentCode != null) {
            IntentNodeDO parent = intentNodeMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<IntentNodeDO>()
                            .eq(IntentNodeDO::getIntentCode, parentCode)
                            .eq(IntentNodeDO::getDeleteFlag, DeleteFlagEnum.NORMAL.getCode()));
            if (parent != null) {
                node.setParentId(parent.getId());
            }
        }

        intentNodeMapper.insert(node);
        nodeIds.add(node.getId());
        return node.getId();
    }
}
