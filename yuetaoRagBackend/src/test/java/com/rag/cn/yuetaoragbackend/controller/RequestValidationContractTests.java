package com.rag.cn.yuetaoragbackend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.rag.cn.yuetaoragbackend.dto.req.ChatReq;
import com.rag.cn.yuetaoragbackend.dto.req.ChatStreamReq;
import com.rag.cn.yuetaoragbackend.dto.req.CreateChatMessageReq;
import com.rag.cn.yuetaoragbackend.dto.req.CreateChatSessionReq;
import com.rag.cn.yuetaoragbackend.dto.req.CreateKnowledgeBaseReq;
import com.rag.cn.yuetaoragbackend.dto.req.DeleteChatSessionReq;
import com.rag.cn.yuetaoragbackend.dto.req.DeleteKnowledgeBaseReq;
import com.rag.cn.yuetaoragbackend.dto.req.DeleteKnowledgeDocumentReq;
import com.rag.cn.yuetaoragbackend.dto.req.LoginReq;
import com.rag.cn.yuetaoragbackend.dto.req.StopChatStreamReq;
import com.rag.cn.yuetaoragbackend.dto.req.SplitKnowledgeDocumentReq;
import com.rag.cn.yuetaoragbackend.dto.req.UpdateKnowledgeBaseReq;
import com.rag.cn.yuetaoragbackend.dto.req.UpdateKnowledgeDocumentReq;
import com.rag.cn.yuetaoragbackend.dto.req.UpdateKnowledgeDocumentStatusReq;
import com.rag.cn.yuetaoragbackend.framework.web.GlobalExceptionHandler;
import com.rag.cn.yuetaoragbackend.service.ChatSessionService;
import com.rag.cn.yuetaoragbackend.service.KnowledgeBaseService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.RequestBody;

@ExtendWith(MockitoExtension.class)
class RequestValidationContractTests {

    @Mock
    private KnowledgeBaseService knowledgeBaseService;

    @Mock
    private ChatSessionService chatSessionService;

    @Test
    void shouldRequireValidOnTypedRequestBodyParameters() throws NoSuchMethodException {
        assertRequestBodyParameterValidated(AuthController.class, "login", LoginReq.class);
        assertRequestBodyParameterValidated(ChatSessionController.class, "createChatSession", CreateChatSessionReq.class);
        assertRequestBodyParameterValidated(ChatSessionController.class, "deleteChatSession", DeleteChatSessionReq.class);
        assertRequestBodyParameterValidated(ChatMessageController.class, "chat", ChatReq.class);
        assertRequestBodyParameterValidated(ChatMessageController.class, "chatStream", ChatStreamReq.class);
        assertRequestBodyParameterValidated(ChatMessageController.class, "stopChatStream", StopChatStreamReq.class);
        assertRequestBodyParameterValidated(ChatMessageController.class, "createChatMessage", CreateChatMessageReq.class);
        assertRequestBodyParameterValidated(KnowledgeBaseController.class, "createKnowledgeBase", CreateKnowledgeBaseReq.class);
        assertRequestBodyParameterValidated(KnowledgeBaseController.class, "updateKnowledgeBase", UpdateKnowledgeBaseReq.class);
        assertRequestBodyParameterValidated(KnowledgeBaseController.class, "deleteKnowledgeBase", DeleteKnowledgeBaseReq.class);
        assertRequestBodyParameterValidated(KnowledgeDocumentController.class, "updateKnowledgeDocument", UpdateKnowledgeDocumentReq.class);
        assertRequestBodyParameterValidated(KnowledgeDocumentController.class, "deleteKnowledgeDocument", DeleteKnowledgeDocumentReq.class);
        assertRequestBodyParameterValidated(KnowledgeDocumentController.class, "splitKnowledgeDocument", SplitKnowledgeDocumentReq.class);
        assertRequestBodyParameterValidated(KnowledgeDocumentController.class, "updateKnowledgeDocumentStatus",
                UpdateKnowledgeDocumentStatusReq.class);
    }

    @Test
    void shouldDeclareConstraintsOnRequiredRequestFields() throws NoSuchFieldException {
        assertFieldConstraint(LoginReq.class, "username", NotBlank.class);
        assertFieldConstraint(LoginReq.class, "password", NotBlank.class);
        assertFieldType(LoginReq.class, "rememberMe", Boolean.class);
        assertFieldConstraint(ChatReq.class, "sessionId", NotNull.class);
        assertFieldConstraint(ChatReq.class, "message", NotBlank.class);
        assertFieldConstraint(ChatStreamReq.class, "sessionId", NotNull.class);
        assertFieldConstraint(ChatStreamReq.class, "message", NotBlank.class);
        assertFieldConstraint(StopChatStreamReq.class, "sessionId", NotNull.class);
        assertFieldConstraint(StopChatStreamReq.class, "traceId", NotBlank.class);
        assertFieldConstraint(CreateChatMessageReq.class, "sessionId", NotNull.class);
        assertFieldConstraint(CreateChatMessageReq.class, "role", NotBlank.class);
        assertFieldConstraint(CreateChatMessageReq.class, "content", NotBlank.class);
        assertFieldConstraint(CreateChatMessageReq.class, "sequenceNo", NotNull.class);
        assertFieldConstraint(CreateKnowledgeBaseReq.class, "name", NotBlank.class);
        assertFieldConstraint(CreateKnowledgeBaseReq.class, "collectionName", NotBlank.class);
        assertFieldConstraint(DeleteChatSessionReq.class, "id", NotNull.class);
        assertFieldConstraint(UpdateKnowledgeBaseReq.class, "id", NotNull.class);
        assertFieldConstraint(UpdateKnowledgeBaseReq.class, "name", NotBlank.class);
        assertFieldConstraint(DeleteKnowledgeBaseReq.class, "id", NotNull.class);
        assertFieldConstraint(UpdateKnowledgeDocumentReq.class, "id", NotNull.class);
        assertFieldConstraint(DeleteKnowledgeDocumentReq.class, "id", NotNull.class);
        assertFieldConstraint(SplitKnowledgeDocumentReq.class, "documentId", NotNull.class);
        assertFieldConstraint(UpdateKnowledgeDocumentStatusReq.class, "id", NotNull.class);
        assertFieldConstraint(UpdateKnowledgeDocumentStatusReq.class, "status", NotBlank.class);
    }

    @Test
    void shouldRejectInvalidKnowledgeBaseCreateRequestBeforeCallingService() throws Exception {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        try {
            MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new KnowledgeBaseController(knowledgeBaseService))
                    .setControllerAdvice(new GlobalExceptionHandler())
                    .setValidator(validator)
                    .build();

            MvcResult mvcResult = mockMvc.perform(MockMvcRequestBuilders.post("/knowledge-bases/create")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "name":"   ",
                                      "collectionName":"kb-test"
                                    }
                                    """))
                    .andReturn();

            String body = mvcResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
            assertThat(mvcResult.getResponse().getStatus()).isEqualTo(200);
            assertThat(body).contains("\"code\":\"A000001\"");
            assertThat(body).contains("知识库名称不能为空");
            verify(knowledgeBaseService, never()).createKnowledgeBase(any(CreateKnowledgeBaseReq.class));
        } finally {
            validator.close();
        }
    }

    @Test
    void shouldUseTypedValidatedRequestForDeleteChatSession() throws Exception {
        Method method = Arrays.stream(ChatSessionController.class.getMethods())
                .filter(each -> each.getName().equals("deleteChatSession"))
                .findFirst()
                .orElseThrow();
        assertThat(method.getParameters()[0].getType().getSimpleName()).isEqualTo("DeleteChatSessionReq");
        assertThat(Map.class.isAssignableFrom(method.getParameters()[0].getType())).isFalse();
        assertThat(method.getParameters()[0].isAnnotationPresent(RequestBody.class)).isTrue();
        assertThat(method.getParameters()[0].isAnnotationPresent(Valid.class)).isTrue();

        Field idField = method.getParameters()[0].getType().getDeclaredField("id");
        assertThat(idField.isAnnotationPresent(NotNull.class)).isTrue();
    }

    @Test
    void shouldRejectDeleteChatSessionRequestWithoutIdBeforeCallingService() throws Exception {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        try {
            MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ChatSessionController(chatSessionService))
                    .setControllerAdvice(new GlobalExceptionHandler())
                    .setValidator(validator)
                    .build();

            MvcResult mvcResult = mockMvc.perform(MockMvcRequestBuilders.post("/chat-sessions/delete")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andReturn();

            String body = mvcResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
            assertThat(mvcResult.getResponse().getStatus()).isEqualTo(200);
            assertThat(body).contains("\"code\":\"A000001\"");
            assertThat(body).contains("会话ID不能为空");
            verify(chatSessionService, never()).deleteChatSession(any());
        } finally {
            validator.close();
        }
    }

    private void assertRequestBodyParameterValidated(Class<?> controllerClass, String methodName, Class<?> requestType)
            throws NoSuchMethodException {
        Method method = controllerClass.getMethod(methodName, requestType);
        assertThat(method.getParameterCount()).isEqualTo(1);
        assertThat(method.getParameters()[0].isAnnotationPresent(RequestBody.class))
                .as("%s.%s should keep @RequestBody", controllerClass.getSimpleName(), methodName)
                .isTrue();
        assertThat(method.getParameters()[0].isAnnotationPresent(Valid.class))
                .as("%s.%s should add @Valid", controllerClass.getSimpleName(), methodName)
                .isTrue();
    }

    private void assertFieldConstraint(Class<?> requestType, String fieldName, Class<? extends Annotation> annotationType)
            throws NoSuchFieldException {
        Field field = requestType.getDeclaredField(fieldName);
        assertThat(field.isAnnotationPresent(annotationType))
                .as("%s.%s should declare %s", requestType.getSimpleName(), fieldName, annotationType.getSimpleName())
                .isTrue();
    }

    private void assertFieldType(Class<?> requestType, String fieldName, Class<?> fieldType) throws NoSuchFieldException {
        Field field = requestType.getDeclaredField(fieldName);
        assertThat(field.getType())
                .as("%s.%s should use %s", requestType.getSimpleName(), fieldName, fieldType.getSimpleName())
                .isEqualTo(fieldType);
    }
}
