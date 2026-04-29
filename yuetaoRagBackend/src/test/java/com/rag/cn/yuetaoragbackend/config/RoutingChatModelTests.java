package com.rag.cn.yuetaoragbackend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rag.cn.yuetaoragbackend.config.record.ChatModelCandidateRuntimeRecord;
import com.rag.cn.yuetaoragbackend.config.properties.AiProperties;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * @author zrq
 * 2026/04/29 18:40
 */
@ExtendWith(MockitoExtension.class)
class RoutingChatModelTests {

    @Mock
    private ChatModel primaryModel;

    @Mock
    private ChatModel fallbackModel;

    @Test
    void shouldFallbackToNextCandidateWhenPrimaryFails() {
        when(primaryModel.call(org.mockito.ArgumentMatchers.any(Prompt.class)))
                .thenThrow(new RuntimeException("primary down"));
        when(fallbackModel.call(org.mockito.ArgumentMatchers.any(Prompt.class)))
                .thenReturn(successResponse("ok"));

        RoutingChatModel routingChatModel = new RoutingChatModel(
                List.of(
                        runtime("qwen-plus", "bailian", "qwen-plus", 100, primaryModel),
                        runtime("glm-4.7", "siliconflow", "Pro/zai-org/GLM-4.7", 60, fallbackModel)
                ),
                circuitBreakerProperties());

        ChatResponse response = routingChatModel.call(new Prompt("hello"));

        assertThat(response.getResult().getOutput().getText()).isEqualTo("ok");
        assertThat(routingChatModel.currentModelInfo().candidateId()).isEqualTo("glm-4.7");
        verify(primaryModel, times(1)).call(org.mockito.ArgumentMatchers.any(Prompt.class));
        verify(fallbackModel, times(1)).call(org.mockito.ArgumentMatchers.any(Prompt.class));
    }

    @Test
    void shouldOpenPrimaryCircuitAfterThresholdReached() {
        when(primaryModel.call(org.mockito.ArgumentMatchers.any(Prompt.class)))
                .thenThrow(new RuntimeException("primary down"));
        when(fallbackModel.call(org.mockito.ArgumentMatchers.any(Prompt.class)))
                .thenReturn(successResponse("ok"));

        RoutingChatModel routingChatModel = new RoutingChatModel(
                List.of(
                        runtime("qwen-plus", "bailian", "qwen-plus", 100, primaryModel),
                        runtime("glm-4.7", "siliconflow", "Pro/zai-org/GLM-4.7", 60, fallbackModel)
                ),
                circuitBreakerProperties());

        routingChatModel.call(new Prompt("hello-1"));
        routingChatModel.call(new Prompt("hello-2"));
        routingChatModel.call(new Prompt("hello-3"));
        routingChatModel.call(new Prompt("hello-4"));

        assertThat(routingChatModel.currentModelInfo().candidateId()).isEqualTo("glm-4.7");
        verify(primaryModel, times(3)).call(org.mockito.ArgumentMatchers.any(Prompt.class));
        verify(fallbackModel, times(4)).call(org.mockito.ArgumentMatchers.any(Prompt.class));
    }

    @Test
    void shouldThrowWhenNoCandidateAvailable() {
        when(primaryModel.call(org.mockito.ArgumentMatchers.any(Prompt.class)))
                .thenThrow(new RuntimeException("primary down"));

        RoutingChatModel routingChatModel = new RoutingChatModel(
                List.of(runtime("qwen-plus", "bailian", "qwen-plus", 100, primaryModel)),
                circuitBreakerProperties());

        assertThatThrownBy(() -> routingChatModel.call(new Prompt("hello")))
                .isInstanceOf(RuntimeException.class);
    }

    private ChatModelCandidateRuntimeRecord runtime(String id, String provider, String modelName, int priority, ChatModel chatModel) {
        return new ChatModelCandidateRuntimeRecord(
                id, provider, modelName, priority, chatModel, new ChatModelStateHolder());
    }

    private AiProperties.CircuitBreakerProperties circuitBreakerProperties() {
        AiProperties.CircuitBreakerProperties properties = new AiProperties.CircuitBreakerProperties();
        properties.setFailureThreshold(3);
        properties.setOpenDurationSeconds(60);
        properties.setHalfOpenMaxProbes(1);
        properties.setFirstTokenTimeoutMillis(8000);
        return properties;
    }

    private ChatResponse successResponse(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }
}
