package com.rag.cn.yuetaoragbackend.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.cn.yuetaoragbackend.dao.projection.RetrievedChunk;
import com.rag.cn.yuetaoragbackend.config.RoutingChatModel;
import com.rag.cn.yuetaoragbackend.config.record.ChatModelCandidateRuntimeRecord;
import com.rag.cn.yuetaoragbackend.config.properties.AiProperties;
import com.rag.cn.yuetaoragbackend.config.record.ChatModelInfoRecord;
import com.rag.cn.yuetaoragbackend.config.record.StreamContentRecord;
import com.rag.cn.yuetaoragbackend.dto.resp.IntentNodeTreeResp;
import com.rag.cn.yuetaoragbackend.framework.errorcode.BaseErrorCode;
import com.rag.cn.yuetaoragbackend.framework.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

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

    private static final Pattern JSON_BLOCK_PATTERN = Pattern.compile("\\{.*?}", Pattern.DOTALL);

    private final AiProperties aiProperties;
    private final RoutingChatModel chatModel;
    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;

    public String classifyQuestionIntent(String question, List<String> recentMessages) {
        String classifyPrompt = renderPrompt("prompt/intent-classifier.st", Map.of(
                        "history", renderHistory(recentMessages),
                        "question", safeText(question)
                )
        );
        String intentContent = chatCompletion("", List.of(userMessage(classifyPrompt)));
        log.info("[INTENT] 意图分类LLM原始返回: {}", shorten(intentContent, 200));
        return parseIntent(intentContent, question);
    }

    public String rewriteQuestion(String question, List<String> recentMessages) {
        return runRewrite(question, recentMessages);
    }

    public String generateAnswer(String originalQuestion, String rewrittenQuery,
                                 List<String> recentMessages,
                                 List<RetrievedChunk> citations,
                                 String intentSnippet) {
        return generateAnswer(originalQuestion, rewrittenQuery, recentMessages, citations, intentSnippet, null);
    }

    public String generateAnswer(String originalQuestion, String rewrittenQuery,
                                 List<String> recentMessages,
                                 List<RetrievedChunk> citations,
                                 String intentSnippet,
                                 String promptTemplate) {
        String prompt = renderKnowledgePrompt(promptTemplate, originalQuestion, rewrittenQuery,
                recentMessages, citations, intentSnippet);
        return chatCompletion("", List.of(userMessage(prompt)));
    }

    public String generateSummary(List<String> historyTexts, int maxChars) {
        String prompt = renderPrompt("prompt/conversation-summary.st", Map.of(
                "history", renderHistory(historyTexts),
                "summary_max_chars", String.valueOf(maxChars)));
        return chatCompletion("", List.of(userMessage(prompt)));
    }

    public String generateChitchatAnswer(String question, List<String> recentMessages) {
        return generateChitchatAnswer(question, recentMessages, null);
    }

    public String generateChitchatAnswer(String question, List<String> recentMessages, String promptTemplate) {
        String prompt = renderSystemPrompt(promptTemplate, question, recentMessages, "无");
        return chatCompletion("", List.of(userMessage(prompt)));
    }

    public double scoreLeafIntent(String question, IntentNodeTreeResp leafNode) {
        String prompt = renderPrompt("prompt/intent-leaf-scorer.st", Map.of(
                "question", safeText(question),
                "node_name", safeText(leafNode.getName()),
                "node_description", safeText(leafNode.getDescription()),
                "node_examples", safeText(leafNode.getExamples())));
        String content = chatCompletion("", List.of(userMessage(prompt)));
        try {
            JsonNode root = objectMapper.readTree(extractJson(content));
            double score = root.path("score").asDouble(0.0);
            log.info("[INTENT] 叶子节点LLM打分: intentCode={}, rawResponse={}", leafNode.getIntentCode(), shorten(content, 100));
            return score;
        } catch (Exception e) {
            log.warn("意图打分解析失败, intentCode={}", leafNode.getIntentCode(), e);
            return 0.0;
        }
    }

    public ChatModelInfoRecord currentModelInfo() {
        var current = chatModel.currentModelInfo();
        return new ChatModelInfoRecord(current.provider(), current.modelName());
    }

    public List<String> streamingCandidateIds() {
        List<String> streamingEnabledIds = aiProperties.getChat().getCandidates().stream()
                .filter(each -> Boolean.TRUE.equals(each.getStreamingEnabled()) && Boolean.TRUE.equals(each.getEnabled()))
                .map(AiProperties.ChatCandidateProperties::getId)
                .toList();
        return chatModel.candidatesForStreaming().stream()
                .map(ChatModelCandidateRuntimeRecord::id)
                .filter(streamingEnabledIds::contains)
                .toList();
    }

    public List<String> thinkingCandidateIds() {
        List<String> thinkingModelIds = aiProperties.getChat().getCandidates().stream()
                .filter(each -> Boolean.TRUE.equals(each.getSupportsThinking()) && Boolean.TRUE.equals(each.getEnabled()))
                .map(AiProperties.ChatCandidateProperties::getId)
                .toList();
        List<String> available = streamingCandidateIds();
        return thinkingModelIds.stream().filter(available::contains).toList();
    }

    public boolean tryAcquireStreamingCandidate(String candidateId) {
        return candidateById(candidateId).tryAcquire(aiProperties.getCircuitBreaker());
    }

    public void markStreamingCandidateSuccess(String candidateId) {
        chatModel.markStreamingSuccess(candidateById(candidateId));
    }

    public void markStreamingCandidateFailure(String candidateId) {
        chatModel.markStreamingFailure(candidateById(candidateId));
    }

    public ChatModelInfoRecord candidateInfo(String candidateId) {
        ChatModelCandidateRuntimeRecord candidate = candidateById(candidateId);
        return new ChatModelInfoRecord(candidate.provider(), candidate.modelName());
    }

    public Flux<String> streamChitchatByCandidate(String candidateId, String question, List<String> recentMessages) {
        return streamChitchatByCandidate(candidateId, question, recentMessages, null);
    }

    public Flux<String> streamChitchatByCandidate(String candidateId, String question,
                                                  List<String> recentMessages, String promptTemplate) {
        String prompt = renderSystemPrompt(promptTemplate, question, recentMessages, "无");
        return streamByCandidate(candidateId, new Prompt(new UserMessage(prompt)));
    }

    public Flux<String> streamKnowledgeAnswerByCandidate(String candidateId, String originalQuestion,
                                                         String rewrittenQuery, List<String> recentMessages,
                                                         List<RetrievedChunk> citations,
                                                         String intentSnippet) {
        return streamKnowledgeAnswerByCandidate(candidateId, originalQuestion, rewrittenQuery,
                recentMessages, citations, intentSnippet, null);
    }

    public Flux<String> streamKnowledgeAnswerByCandidate(String candidateId, String originalQuestion,
                                                         String rewrittenQuery, List<String> recentMessages,
                                                         List<RetrievedChunk> citations,
                                                         String intentSnippet,
                                                         String promptTemplate) {
        String prompt = renderKnowledgePrompt(promptTemplate, originalQuestion, rewrittenQuery,
                recentMessages, citations, intentSnippet);
        return streamByCandidate(candidateId, new Prompt(new UserMessage(prompt)));
    }

    public Flux<StreamContentRecord> streamThinkingChitchatByCandidate(String candidateId, String question,
                                                                 List<String> recentMessages) {
        return streamThinkingChitchatByCandidate(candidateId, question, recentMessages, null);
    }

    public Flux<StreamContentRecord> streamThinkingChitchatByCandidate(String candidateId, String question,
                                                                 List<String> recentMessages,
                                                                 String promptTemplate) {
        String prompt = renderSystemPrompt(promptTemplate, question, recentMessages, "无");
        return streamThinkingByCandidate(candidateId, new Prompt(new UserMessage(prompt)));
    }

    public Flux<StreamContentRecord> streamThinkingKnowledgeAnswerByCandidate(String candidateId, String originalQuestion,
                                                                        String rewrittenQuery, List<String> recentMessages,
                                                                        List<RetrievedChunk> citations,
                                                                        String intentSnippet) {
        return streamThinkingKnowledgeAnswerByCandidate(candidateId, originalQuestion, rewrittenQuery,
                recentMessages, citations, intentSnippet, null);
    }

    public Flux<StreamContentRecord> streamThinkingKnowledgeAnswerByCandidate(String candidateId, String originalQuestion,
                                                                        String rewrittenQuery, List<String> recentMessages,
                                                                        List<RetrievedChunk> citations,
                                                                        String intentSnippet,
                                                                        String promptTemplate) {
        String prompt = renderKnowledgePrompt(promptTemplate, originalQuestion, rewrittenQuery,
                recentMessages, citations, intentSnippet);
        return streamThinkingByCandidate(candidateId, new Prompt(new UserMessage(prompt)));
    }

    private String chatCompletion(String systemPrompt, List<Map<String, String>> messages) {
        String userContent = messages.isEmpty() ? "" : safeText(messages.get(messages.size() - 1).get("content"));
        Prompt prompt = StringUtils.hasText(systemPrompt)
                ? new Prompt(new SystemMessage(systemPrompt), new UserMessage(userContent))
                : new Prompt(new UserMessage(userContent));
        ChatResponse response = chatModel.call(prompt);
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            throw new ServiceException("聊天模型返回为空");
        }
        return response.getResult().getOutput().getText();
    }

    private String renderHistory(List<String> recentMessages) {
        if (recentMessages == null || recentMessages.isEmpty()) {
            return "无";
        }
        return String.join("\n", recentMessages);
    }

    private String renderIntentSnippet(String intentSnippet) {
        if (!StringUtils.hasText(intentSnippet)) {
            return "";
        }
        return "# 意图专项规则\n" + intentSnippet;
    }

    private String renderCitations(List<RetrievedChunk> citations) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < citations.size(); i++) {
            RetrievedChunk each = citations.get(i);
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
            JsonNode subQuestions = root.path("sub_questions");
            int subCount = subQuestions.isArray() ? subQuestions.size() : 0;
            log.info("[REWRITE] Query改写LLM原始返回: rewrite={}, shouldSplit={}, subQuestionCount={}",
                    shorten(rewrite, 120), shouldSplit, subCount);
            if (!StringUtils.hasText(rewrite)) {
                rewrite = question;
            }
            if (!shouldSplit) {
                return rewrite;
            }
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
            log.warn("[REWRITE] Query改写解析失败，回退原问题，rawResponse={}", shorten(content, 200), ex);
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
            log.warn("[INTENT] 意图识别解析失败，回退默认策略", ex);
        }
        String guessed = guessIntent(question);
        log.info("[INTENT] 回退至关键词启发式: guessedIntent={}", guessed);
        return guessed;
    }

    private String renderPrompt(String location, Map<String, String> variables) {
        return renderTemplate(loadPrompt(location), variables);
    }

    private String renderKnowledgePrompt(String promptTemplate, String originalQuestion, String rewrittenQuery,
                                         List<String> recentMessages, List<RetrievedChunk> citations,
                                         String intentSnippet) {
        return renderTemplate(resolvePromptTemplate(promptTemplate, "prompt/answer-chat-kb.st"), Map.of(
                "history", renderHistory(recentMessages),
                "question", safeText(originalQuestion),
                "rewritten_question", safeText(rewrittenQuery),
                "retrieved_knowledge", renderCitations(citations),
                "intent_snippet", renderIntentSnippet(intentSnippet)));
    }

    private String renderSystemPrompt(String promptTemplate, String question,
                                      List<String> recentMessages, String retrievedKnowledge) {
        return renderTemplate(resolvePromptTemplate(promptTemplate, "prompt/answer-chat-system.st"), Map.of(
                "history", renderHistory(recentMessages),
                "question", safeText(question),
                "retrieved_knowledge", safeText(retrievedKnowledge)));
    }

    private String renderTemplate(String rendered, Map<String, String> variables) {
        for (Map.Entry<String, String> each : variables.entrySet()) {
            rendered = rendered.replace("{" + each.getKey() + "}", safeText(each.getValue()));
        }
        return rendered;
    }

    private String resolvePromptTemplate(String promptTemplate, String defaultLocation) {
        return StringUtils.hasText(promptTemplate) ? promptTemplate : loadPrompt(defaultLocation);
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

    private String shorten(String text, int maxLength) {
        if (!StringUtils.hasText(text) || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
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

    private Flux<String> streamByCandidate(String candidateId, Prompt prompt) {
        return candidateById(candidateId).chatModel().stream(prompt)
                .map(this::extractStreamText)
                .filter(StringUtils::hasText);
    }

    private Flux<StreamContentRecord> streamThinkingByCandidate(String candidateId, Prompt prompt) {
        OpenAiChatOptions.Builder builder = OpenAiChatOptions.builder();
        Map<String, Object> extraBody = aiProperties.getChat().getCandidates().stream()
                .filter(c -> Objects.equals(c.getId(), candidateId))
                .findFirst()
                .map(AiProperties.ChatCandidateProperties::getThinkingExtraBody)
                .orElse(Map.of());
        if (!extraBody.isEmpty()) {
            builder.extraBody(extraBody);
        } else {
            builder.reasoningEffort("medium");
        }
        Prompt thinkingPrompt = new Prompt(prompt.getInstructions(), builder.build());
        return candidateById(candidateId).chatModel().stream(thinkingPrompt)
                .map(this::extractStreamContent)
                .filter(sc -> sc.hasThinking() || sc.hasContent());
    }

    private StreamContentRecord extractStreamContent(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return new StreamContentRecord(null, null);
        }
        String reasoning = null;
        Map<String, Object> metadata = response.getResult().getOutput().getMetadata();
        Object reasoningObj = metadata != null ? metadata.get("reasoningContent") : null;
        if (reasoningObj instanceof String rs && StringUtils.hasText(rs)) {
            reasoning = rs;
        }
        String content = response.getResult().getOutput().getText();
        return new StreamContentRecord(reasoning, content);
    }

    private String extractStreamText(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return null;
        }
        return response.getResult().getOutput().getText();
    }

    private ChatModelCandidateRuntimeRecord candidateById(String candidateId) {
        return chatModel.candidatesForStreaming().stream()
                .filter(each -> Objects.equals(each.id(), candidateId))
                .findFirst()
                .orElseThrow(() -> new ServiceException("未找到流式候选：" + candidateId));
    }
}
