package com.rag.cn.yuetaoragbackend.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.cn.yuetaoragbackend.config.properties.AiProperties;
import com.rag.cn.yuetaoragbackend.framework.errorcode.BaseErrorCode;
import com.rag.cn.yuetaoragbackend.framework.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author zrq
 * 2026/04/29 15:40
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatModelGateway {

    private static final Pattern JSON_BLOCK_PATTERN = Pattern.compile("\\{.*}", Pattern.DOTALL);

    private final AiProperties aiProperties;
    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;

    public String classifyQuestionIntent(String question, List<String> recentMessages) {
        String classifyPrompt = renderPrompt("prompt/intent-classifier.st", Map.of(
                "history", renderHistory(recentMessages),
                "question", safeText(question)));
        String intentContent = chatCompletion("", List.of(userMessage(classifyPrompt)));
        return parseIntent(intentContent, question);
    }

    public String rewriteQuestion(String question, List<String> recentMessages) {
        return runRewrite(question, recentMessages);
    }

    public String generateAnswer(String originalQuestion, String rewrittenQuery,
                                 List<String> recentMessages,
                                 List<RagRetrievalService.RetrievedChunk> citations) {
        String prompt = renderPrompt("prompt/answer-chat-kb.st", Map.of(
                "history", renderHistory(recentMessages),
                "question", safeText(originalQuestion),
                "rewritten_question", safeText(rewrittenQuery),
                "retrieved_knowledge", renderCitations(citations)));
        return chatCompletion("", List.of(userMessage(prompt)));
    }

    public String generateChitchatAnswer(String question, List<String> recentMessages) {
        String prompt = renderPrompt("prompt/answer-chat-system.st", Map.of(
                "history", renderHistory(recentMessages),
                "question", safeText(question),
                "retrieved_knowledge", "无"));
        return chatCompletion("", List.of(userMessage(prompt)));
    }

    public ModelInfo currentModelInfo() {
        AiProperties.ChatCandidateProperties candidate = resolveChatCandidate();
        return new ModelInfo(candidate.getProvider(), candidate.getModel());
    }

    private String chatCompletion(String systemPrompt, List<Map<String, String>> messages) {
        String userContent = messages.isEmpty() ? "" : safeText(messages.get(messages.size() - 1).get("content"));
        Prompt prompt = StringUtils.hasText(systemPrompt)
                ? new Prompt(new SystemMessage(systemPrompt), new UserMessage(userContent))
                : new Prompt(new UserMessage(userContent));
        var response = chatModel.call(prompt);
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            throw new ServiceException("聊天模型返回为空");
        }
        return response.getResult().getOutput().getText();
    }

    private AiProperties.ChatCandidateProperties resolveChatCandidate() {
        return aiProperties.getChat().getCandidates().stream()
                .filter(each -> Boolean.TRUE.equals(each.getEnabled()))
                .filter(each -> Objects.equals(each.getId(), aiProperties.getChat().getDefaultModel()))
                .findFirst()
                .orElseThrow(() -> new ServiceException("未找到默认聊天模型配置：" + aiProperties.getChat().getDefaultModel()));
    }

    private String renderHistory(List<String> recentMessages) {
        if (recentMessages == null || recentMessages.isEmpty()) {
            return "无";
        }
        return String.join("\n", recentMessages);
    }

    private String renderCitations(List<RagRetrievalService.RetrievedChunk> citations) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < citations.size(); i++) {
            RagRetrievalService.RetrievedChunk each = citations.get(i);
            builder.append('[').append(i + 1).append("] ")
                    .append(each.documentTitle())
                    .append("（切片#").append(each.chunkNo()).append("）")
                    .append("：")
                    .append(each.effectiveContent())
                    .append('\n');
        }
        return builder.toString();
    }

    private String runRewrite(String question, List<String> recentMessages) {
        String prompt = renderPrompt("prompt/user-question-rewrite.st", Map.of(
                "history", renderHistory(recentMessages),
                "question", safeText(question)));
        String content = chatCompletion("", List.of(userMessage(prompt)));
        try {
            JsonNode root = objectMapper.readTree(extractJson(content));
            String rewrite = root.path("rewrite").asText();
            boolean shouldSplit = root.path("should_split").asBoolean(false);
            if (!StringUtils.hasText(rewrite)) {
                rewrite = question;
            }
            if (!shouldSplit) {
                return rewrite;
            }
            JsonNode subQuestions = root.path("sub_questions");
            if (!subQuestions.isArray() || subQuestions.size() == 0) {
                return rewrite;
            }
            LinkedHashSet<String> collected = new LinkedHashSet<>();
            for (JsonNode each : subQuestions) {
                String subQuestion = each.asText();
                if (StringUtils.hasText(subQuestion)) {
                    collected.add(subQuestion.trim());
                }
            }
            return collected.isEmpty() ? rewrite : String.join("；", collected);
        } catch (Exception ex) {
            log.warn("Query 改写解析失败，回退原问题", ex);
            return question;
        }
    }

    private String parseIntent(String content, String question) {
        try {
            JsonNode root = objectMapper.readTree(extractJson(content));
            String intent = root.path("intent").asText();
            if ("CHITCHAT".equals(intent) || "KB_QA".equals(intent)) {
                return intent;
            }
        } catch (Exception ex) {
            log.warn("意图识别解析失败，回退默认策略", ex);
        }
        return guessIntent(question);
    }

    private String renderPrompt(String location, Map<String, String> variables) {
        String rendered = loadPrompt(location);
        for (Map.Entry<String, String> each : variables.entrySet()) {
            rendered = rendered.replace("{" + each.getKey() + "}", safeText(each.getValue()));
        }
        return rendered;
    }

    private String loadPrompt(String location) {
        Resource resource = resourceLoader.getResource("classpath:" + location);
        try {
            return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new ServiceException("读取 Prompt 模板失败：" + location, ex, BaseErrorCode.SERVICE_ERROR);
        }
    }

    private String safeText(String text) {
        return text == null ? "" : text;
    }

    private String extractJson(String content) {
        Matcher matcher = JSON_BLOCK_PATTERN.matcher(content);
        if (matcher.find()) {
            return matcher.group();
        }
        return content;
    }

    private String guessIntent(String question) {
        String normalized = question == null ? "" : question.trim();
        if (!StringUtils.hasText(normalized)) {
            return "CHITCHAT";
        }
        String lowercase = normalized.toLowerCase();
        if (lowercase.matches("^(你好|您好|hello|hi|早上好|晚上好|在吗|哈喽).*$")) {
            return "CHITCHAT";
        }
        return "KB_QA";
    }

    private Map<String, String> userMessage(String content) {
        return Map.of("role", "user", "content", content);
    }

    public record ModelInfo(String provider, String modelName) {
    }
}
