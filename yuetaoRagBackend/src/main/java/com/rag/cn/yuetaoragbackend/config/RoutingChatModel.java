package com.rag.cn.yuetaoragbackend.config;

import com.rag.cn.yuetaoragbackend.config.properties.AiProperties;
import com.rag.cn.yuetaoragbackend.config.record.ChatModelCandidateRuntimeRecord;
import com.rag.cn.yuetaoragbackend.config.record.ChatModelRuntimeInfoRecord;
import com.rag.cn.yuetaoragbackend.framework.errorcode.BaseErrorCode;
import com.rag.cn.yuetaoragbackend.framework.exception.RemoteException;
import com.rag.cn.yuetaoragbackend.framework.exception.ServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * @author zrq
 * 2026/04/29 18:20
 */
@Slf4j
public class RoutingChatModel implements ChatModel {

    private final List<ChatModelCandidateRuntimeRecord> candidates;
    private final AiProperties.CircuitBreakerProperties circuitBreakerProperties;
    private volatile ChatModelCandidateRuntimeRecord lastSuccessfulCandidate;

    public RoutingChatModel(List<ChatModelCandidateRuntimeRecord> candidates, AiProperties.CircuitBreakerProperties circuitBreakerProperties) {
        if (CollectionUtils.isEmpty(candidates)) {
            throw new ServiceException("未配置可用的 ChatModel 候选");
        }
        this.candidates = candidates.stream()
                .sorted(Comparator.comparing(ChatModelCandidateRuntimeRecord::priority).reversed())
                .toList();
        this.circuitBreakerProperties = circuitBreakerProperties;
        this.lastSuccessfulCandidate = this.candidates.get(0);
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        RuntimeException lastException = null;
        for (ChatModelCandidateRuntimeRecord candidate : candidates) {
            if (!candidate.tryAcquire(circuitBreakerProperties)) {
                log.warn("[MODEL] 候选模型熔断跳过: candidateId={}, provider={}, model={}, state={}",
                        candidate.id(), candidate.provider(), candidate.modelName(), candidate.state());
                continue;
            }
            try {
                ChatResponse response = candidate.chatModel().call(prompt);
                candidate.onSuccess();
                lastSuccessfulCandidate = candidate;
                log.info("[MODEL] 候选模型调用成功: candidateId={}, provider={}, model={}",
                        candidate.id(), candidate.provider(), candidate.modelName());
                return response;
            } catch (RuntimeException ex) {
                candidate.onFailure(circuitBreakerProperties);
                lastException = ex;
                log.warn("[MODEL] 候选模型调用失败，尝试切换下一个: candidateId={}, provider={}, model={}, state={}, error={}",
                        candidate.id(), candidate.provider(), candidate.modelName(), candidate.state(), ex.getMessage());
            }
        }
        if (lastException != null) {
            log.error("[MODEL] 所有候选模型均已失败，抛出最后一个异常");
            throw lastException;
        }
        throw new RemoteException("当前没有可用的聊天模型候选", BaseErrorCode.REMOTE_ERROR);
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        throw new UnsupportedOperationException("chatstream 流式编排由 ChatMessageServiceImpl 负责");
    }

    @Override
    public ChatOptions getDefaultOptions() {
        return lastSuccessfulCandidate.chatModel().getDefaultOptions();
    }

    public ChatModelRuntimeInfoRecord currentModelInfo() {
        ChatModelCandidateRuntimeRecord current = lastSuccessfulCandidate == null ? candidates.get(0) : lastSuccessfulCandidate;
        return new ChatModelRuntimeInfoRecord(current.provider(), current.modelName(), current.id(), current.state());
    }

    public List<ChatModelCandidateRuntimeRecord> candidatesForStreaming() {
        return this.candidates;
    }

    public void markStreamingSuccess(ChatModelCandidateRuntimeRecord candidate) {
        candidate.onSuccess();
        lastSuccessfulCandidate = candidate;
    }

    public void markStreamingFailure(ChatModelCandidateRuntimeRecord candidate) {
        candidate.onFailure(circuitBreakerProperties);
    }

    public static List<ChatModelCandidateRuntimeRecord> runtimes(List<AiProperties.ChatCandidateProperties> candidates,
                                                                 List<ChatModel> models) {
        if (candidates.size() != models.size()) {
            throw new ServiceException("ChatModel 候选配置与实例数量不一致");
        }
        List<ChatModelCandidateRuntimeRecord> runtimes = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            AiProperties.ChatCandidateProperties candidate = candidates.get(i);
            runtimes.add(new ChatModelCandidateRuntimeRecord(
                    candidate.getId(),
                    candidate.getProvider(),
                    candidate.getModel(),
                    candidate.getPriority(),
                    models.get(i),
                    new ChatModelStateHolder()));
        }
        return runtimes;
    }
}
