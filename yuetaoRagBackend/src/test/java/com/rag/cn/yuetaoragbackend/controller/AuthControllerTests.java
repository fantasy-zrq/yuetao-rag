package com.rag.cn.yuetaoragbackend.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

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
class AuthControllerTests {

    private static final Long USER_ID = 202605060001L;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("delete from t_user where id = ?", USER_ID);
    }

    @Test
    void shouldLoginWhenPasswordMatchesPasswordHash() throws Exception {
        persistUser("auth_user_ok", sha256Hex("right-password"));

        MvcResult mvcResult = mockMvc.perform(MockMvcRequestBuilders.post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username":"auth_user_ok",
                                  "password":"right-password"
                                }
                                """))
                .andReturn();

        String body = mvcResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(mvcResult.getResponse().getStatus()).isEqualTo(200);
        assertThat(body).contains("\"code\":\"0\"");
        assertThat(body).contains("\"token\":");
    }

    @Test
    void shouldRejectWhenPasswordDoesNotMatchPasswordHash() throws Exception {
        persistUser("auth_user_bad", sha256Hex("right-password"));

        MvcResult mvcResult = mockMvc.perform(MockMvcRequestBuilders.post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username":"auth_user_bad",
                                  "password":"wrong-password"
                                }
                                """))
                .andReturn();

        String body = mvcResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(mvcResult.getResponse().getStatus()).isEqualTo(200);
        assertThat(body).contains("\"code\":\"A000001\"");
        assertThat(body).contains("用户名或密码错误");
    }

    @Test
    void shouldRejectBlankUsernameByValidation() throws Exception {
        MvcResult mvcResult = mockMvc.perform(MockMvcRequestBuilders.post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username":"   ",
                                  "password":"right-password"
                                }
                                """))
                .andReturn();

        String body = mvcResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(mvcResult.getResponse().getStatus()).isEqualTo(200);
        assertThat(body).contains("\"code\":\"A000001\"");
        assertThat(body).contains("用户名不能为空");
    }

    @Test
    void shouldUseAuthorizationHeaderAcrossLoginCurrentUserAndLogout() throws Exception {
        persistUser("auth_user_flow", sha256Hex("right-password"));

        MvcResult loginResult = mockMvc.perform(MockMvcRequestBuilders.post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username":"auth_user_flow",
                                  "password":"right-password",
                                  "rememberMe":true
                                }
                                """))
                .andReturn();

        String loginBody = loginResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        String token = readToken(loginBody);
        assertThat(token).isNotBlank();

        MvcResult currentUserResult = mockMvc.perform(MockMvcRequestBuilders.get("/user/me")
                        .header("Authorization", token))
                .andReturn();
        String currentUserBody = currentUserResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(currentUserResult.getResponse().getStatus()).isEqualTo(200);
        assertThat(currentUserBody).contains("\"code\":\"0\"");
        assertThat(currentUserBody).contains("\"username\":\"auth_user_flow\"");

        MvcResult logoutResult = mockMvc.perform(MockMvcRequestBuilders.post("/auth/logout")
                        .header("Authorization", token))
                .andReturn();
        String logoutBody = logoutResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(logoutResult.getResponse().getStatus()).isEqualTo(200);
        assertThat(logoutBody).contains("\"code\":\"0\"");

        MvcResult afterLogoutResult = mockMvc.perform(MockMvcRequestBuilders.get("/user/me")
                        .header("Authorization", token))
                .andReturn();
        String afterLogoutBody = afterLogoutResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(afterLogoutResult.getResponse().getStatus()).isEqualTo(200);
        assertThat(afterLogoutBody).contains("\"code\":\"A000001\"");
        assertThat(afterLogoutBody).contains("未登录或登录已过期");
    }

    private void persistUser(String username, String passwordHash) {
        jdbcTemplate.update("""
                        insert into t_user
                        (id, username, display_name, role_code, department_id, rank_level, status,
                         password_hash, create_time, update_time, delete_flag)
                        values (?, ?, ?, 'ADMIN', 1, 100, 'ENABLED', ?, ?, ?, 0)
                        """,
                USER_ID, username, username, passwordHash, LocalDateTime.now(), LocalDateTime.now());
    }

    private String sha256Hex(String raw) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
        StringBuilder builder = new StringBuilder(digest.length * 2);
        for (byte each : digest) {
            builder.append(String.format("%02x", each));
        }
        return builder.toString();
    }

    private String readToken(String body) throws Exception {
        JsonNode rootNode = objectMapper.readTree(body);
        return rootNode.path("data").path("token").asText();
    }
}
