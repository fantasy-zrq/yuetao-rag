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

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

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
                continue;
            }
            try {
                ChatResponse response = candidate.chatModel().call(prompt);
                candidate.onSuccess();
                lastSuccessfulCandidate = candidate;
                return response;
            } catch (RuntimeException ex) {
                candidate.onFailure(circuitBreakerProperties);
                lastException = ex;
                log.warn("ChatModel 调用失败，尝试切换下一个候选: candidateId={}, provider={}, model={}",
                        candidate.id(), candidate.provider(), candidate.modelName(), ex);
            }
        }
        if (lastException != null) {
            throw lastException;
        }
        throw new RemoteException("当前没有可用的聊天模型候选", BaseErrorCode.REMOTE_ERROR);
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        return stream(prompt, 0, null);
    }

    private Flux<ChatResponse> stream(Prompt prompt, int index, RuntimeException lastException) {
        if (index >= candidates.size()) {
            if (lastException != null) {
                return Flux.error(lastException);
            }
            return Flux.error(new RemoteException("当前没有可用的聊天模型候选", BaseErrorCode.REMOTE_ERROR));
        }
        ChatModelCandidateRuntimeRecord candidate = candidates.get(index);
        if (!candidate.tryAcquire(circuitBreakerProperties)) {
            return stream(prompt, index + 1, lastException);
        }
        AtomicBoolean successMarked = new AtomicBoolean(false);
        Flux<ChatResponse> source = candidate.chatModel().stream(prompt);
        Integer firstTokenTimeoutMillis = circuitBreakerProperties.getFirstTokenTimeoutMillis();
        if (firstTokenTimeoutMillis != null && firstTokenTimeoutMillis > 0) {
            source = source.timeout(Duration.ofMillis(firstTokenTimeoutMillis));
        }
        return source
                .doOnNext(ignored -> {
                    if (successMarked.compareAndSet(false, true)) {
                        candidate.onSuccess();
                        lastSuccessfulCandidate = candidate;
                    }
                })
                .doOnComplete(() -> {
                    if (successMarked.compareAndSet(false, true)) {
                        candidate.onSuccess();
                        lastSuccessfulCandidate = candidate;
                    }
                })
                .onErrorResume(ex -> {
                    candidate.onFailure(circuitBreakerProperties);
                    RuntimeException runtimeException = ex instanceof RuntimeException
                            ? (RuntimeException) ex
                            : new RemoteException("聊天模型流式调用失败", ex, BaseErrorCode.REMOTE_ERROR);
                    log.warn("ChatModel 流式调用失败，尝试切换下一个候选: candidateId={}, provider={}, model={}",
                            candidate.id(), candidate.provider(), candidate.modelName(), ex);
                    return stream(prompt, index + 1, runtimeException);
                });
    }

    @Override
    public ChatOptions getDefaultOptions() {
        return lastSuccessfulCandidate.chatModel().getDefaultOptions();
    }

    public ChatModelRuntimeInfoRecord currentModelInfo() {
        ChatModelCandidateRuntimeRecord current = lastSuccessfulCandidate == null ? candidates.get(0) : lastSuccessfulCandidate;
        return new ChatModelRuntimeInfoRecord(current.provider(), current.modelName(), current.id(), current.state());
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
