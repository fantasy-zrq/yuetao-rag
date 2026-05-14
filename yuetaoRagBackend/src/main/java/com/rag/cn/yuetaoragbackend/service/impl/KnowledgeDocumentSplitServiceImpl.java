package com.rag.cn.yuetaoragbackend.service.impl;

import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.rag.cn.yuetaoragbackend.config.enums.DeleteFlagEnum;
import com.rag.cn.yuetaoragbackend.config.enums.ParseStatusEnum;
import com.rag.cn.yuetaoragbackend.dao.entity.ChunkDO;
import com.rag.cn.yuetaoragbackend.dao.entity.KnowledgeDocumentDO;
import com.rag.cn.yuetaoragbackend.dao.mapper.ChunkMapper;
import com.rag.cn.yuetaoragbackend.dao.mapper.KnowledgeDocumentMapper;
import com.rag.cn.yuetaoragbackend.framework.context.ApplicationContextHolder;
import com.rag.cn.yuetaoragbackend.framework.exception.ClientException;
import com.rag.cn.yuetaoragbackend.service.DocumentChunkLogService;
import com.rag.cn.yuetaoragbackend.service.file.FileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @author zrq
 * 2026/04/27 10:20
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeDocumentSplitServiceImpl {

    public static final String SPLIT_TOPIC = "yuetao-rag-chunk_topic";

    public static final String SPLIT_CONSUMER_GROUP = "yuetao-rag-chunk-consumer_group";

    private static final int CHUNK_BATCH_SIZE = 15;
    private static final int VECTOR_BATCH_SIZE = 10;
    private static final String DEFAULT_SPLIT_FAILURE_MESSAGE = "文档处理失败，请稍后重试";
    private static final String BATCH_LIMIT_FAILURE_MESSAGE = "文档向量化失败：单批次最多支持 10 个分块";

    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final DocumentChunkLogService documentChunkLogService;
    private final ChunkMapper chunkMapper;
    private final FileService fileService;
    private final PgVectorStore chunkVectorStore;
    private final JdbcTemplate jdbcTemplate;

    @Transactional(rollbackFor = Exception.class)
    public void processSplit(Long documentId, Long chunkLogId) {
        long totalStartNanos = System.nanoTime();
        KnowledgeDocumentDO documentDO = knowledgeDocumentMapper.selectOne(Wrappers.<KnowledgeDocumentDO>lambdaQuery()
                .eq(KnowledgeDocumentDO::getId, documentId)
                .eq(KnowledgeDocumentDO::getDeleteFlag, DeleteFlagEnum.NORMAL.getCode()));
        if (documentDO == null) {
            throw new ClientException("文档不存在或已删除：" + documentId);
        }
        if (ParseStatusEnum.FAILED.getCode().equals(documentDO.getParseStatus())) {
            log.warn("跳过已失败文档的切片重试: documentId={}, chunkLogId={}", documentId, chunkLogId);
            return;
        }
        byte[] content = fileService.getObject(documentDO.getStorageBucket(), documentDO.getStorageKey());
        String text = parseDocument(content, documentDO.getTitle());
        List<String> chunkTexts = splitTextByMode(text, documentDO.getChunkMode(), documentDO.getChunkConfig());
        if (chunkTexts.isEmpty()) {
            throw new ClientException("切片结果为空：" + documentId);
        }
        long splitCostMillis = elapsedMillis(totalStartNanos);
        try {
            documentChunkLogService.recordSplitResult(chunkLogId, chunkTexts.size(), splitCostMillis);
        } catch (Exception ex) {
            log.warn("文档分块日志更新失败: action=记录分块结果, documentId={}, chunkLogId={}", documentId, chunkLogId, ex);
        }

        List<Document> vectorDocuments = new ArrayList<>();
        List<ChunkDO> chunkEntities = new ArrayList<>();
        for (int i = 0; i < chunkTexts.size(); i++) {
            String chunkText = chunkTexts.get(i);
            long chunkId = IdWorker.getId();
            ChunkDO chunkDO = new ChunkDO()
                    .setKnowledgeBaseId(documentDO.getKnowledgeBaseId())
                    .setDocumentId(documentId)
                    .setChunkNo(i)
                    .setChunkHash(DigestUtil.sha256Hex(chunkText))
                    .setOriginalContent(chunkText)
                    .setEffectiveContent(chunkText)
                    .setTokenCount(chunkText.length())
                    .setEmbeddingStatus("SUCCESS")
                    .setEnabled(Boolean.TRUE)
                    .setManualEdited(Boolean.FALSE)
                    .setCreatedBy(documentDO.getUpdatedBy())
                    .setUpdatedBy(documentDO.getUpdatedBy());
            chunkDO.setId(chunkId);
            chunkEntities.add(chunkDO);

            vectorDocuments.add(Document.builder()
                    .id(String.valueOf(chunkId))
                    .text(chunkText)
                    .metadata(Map.of(
                            "document_id", String.valueOf(documentId),
                            "chunk_no", i,
                            "collection_name", documentDO.getStorageBucket()))
                    .build());
        }

        batchInsertChunks(chunkEntities);
        long vectorStartNanos = System.nanoTime();
        try {
            batchAddVectorDocuments(vectorDocuments);
        } catch (RuntimeException ex) {
            try {
                currentSplitService().cleanupInsertedVectors(documentId);
            } catch (RuntimeException cleanupEx) {
                log.warn("向量化失败后清理孤儿向量失败: documentId={}, chunkLogId={}", documentId, chunkLogId, cleanupEx);
            }
            throw ex;
        }
        long vectorCostMillis = elapsedMillis(vectorStartNanos);
        try {
            documentChunkLogService.recordVectorResult(chunkLogId, vectorCostMillis);
        } catch (Exception ex) {
            log.warn("文档分块日志更新失败: action=记录向量化结果, documentId={}, chunkLogId={}", documentId, chunkLogId, ex);
        }
        KnowledgeDocumentDO successDO = new KnowledgeDocumentDO();
        successDO.setId(documentId);
        successDO.setParseStatus(ParseStatusEnum.SUCCESS.getCode());
        successDO.setFailReason(null);
        successDO.setUpdatedBy(documentDO.getUpdatedBy());
        knowledgeDocumentMapper.updateById(successDO);
        clearDocumentFailReason(documentId);
        try {
            documentChunkLogService.markSuccess(
                    chunkLogId,
                    chunkTexts.size(),
                    splitCostMillis,
                    vectorCostMillis,
                    elapsedMillis(totalStartNanos));
        } catch (Exception ex) {
            log.warn("文档分块日志更新失败: action=标记分块成功, documentId={}, chunkLogId={}", documentId, chunkLogId, ex);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void markSplitFailed(Long documentId, Long chunkLogId, String errorMessage) {
        String sanitizedErrorMessage = sanitizeSplitErrorMessage(errorMessage);
        KnowledgeDocumentDO failedDO = new KnowledgeDocumentDO();
        failedDO.setId(documentId);
        failedDO.setParseStatus(ParseStatusEnum.FAILED.getCode());
        failedDO.setFailReason(sanitizedErrorMessage);
        knowledgeDocumentMapper.updateById(failedDO);
        try {
            documentChunkLogService.markFailed(chunkLogId, sanitizedErrorMessage);
        } catch (Exception ex) {
            log.warn("文档分块日志更新失败: action=标记分块失败, documentId={}, chunkLogId={}", documentId, chunkLogId, ex);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void markSplitTimeout(Long documentId) {
        KnowledgeDocumentDO failedDO = new KnowledgeDocumentDO();
        failedDO.setId(documentId);
        failedDO.setParseStatus(ParseStatusEnum.FAILED.getCode());
        failedDO.setFailReason("切片超时");
        knowledgeDocumentMapper.updateById(failedDO);

        try {
            documentChunkLogService.markTimeout(documentId);
        } catch (Exception ex) {
            log.warn("文档分块日志更新失败: action=标记分块超时, documentId={}, chunkLogId={}", documentId, null, ex);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void cleanupInsertedVectors(Long documentId) {
        jdbcTemplate.update(
                "delete from t_chunk_vector where metadata->>'document_id' = ?",
                String.valueOf(documentId));
    }

    private void clearDocumentFailReason(Long documentId) {
        knowledgeDocumentMapper.update(null, Wrappers.<KnowledgeDocumentDO>update()
                .eq("id", documentId)
                .set("fail_reason", null));
    }

    private String parseDocument(byte[] content, String filename) {
        List<Document> documents = new TikaDocumentReader(new NamedByteArrayResource(content, filename)).get();
        return documents.stream()
                .map(Document::getText)
                .filter(StringUtils::hasText)
                .collect(Collectors.joining("\n"));
    }

    private List<String> splitTextByMode(String text, String chunkMode, String chunkConfigJson) {
        ChunkConfig chunkConfig = ChunkConfig.fromJson(chunkConfigJson);
        if ("STRUCTURE_AWARE".equals(chunkMode)) {
            return structureAwareSplit(text, chunkConfig.chunkSize(), chunkConfig.chunkOverlap());
        }
        return fixedSplit(text, chunkConfig.chunkSize(), chunkConfig.chunkOverlap());
    }

    private List<String> structureAwareSplit(String text, int chunkSize, int chunkOverlap) {
        List<String> headingSections = splitByMarkdownHeadings(text);
        if (!headingSections.isEmpty()) {
            List<String> chunks = new ArrayList<>();
            for (String headingSection : headingSections) {
                if (headingSection.length() > chunkSize) {
                    chunks.addAll(fixedSplit(headingSection, chunkSize, chunkOverlap));
                } else {
                    chunks.add(headingSection);
                }
            }
            return chunks;
        }
        List<String> paragraphs = java.util.Arrays.stream(text.split("\\n\\s*\\n+"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
        if (!paragraphs.isEmpty()) {
            return flattenSegments(paragraphs, chunkSize, chunkOverlap);
        }
        List<String> sentences = Arrays.stream(text.split("(?<=[。！？.!?])"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
        if (!sentences.isEmpty()) {
            return flattenSegments(sentences, chunkSize, chunkOverlap);
        }
        return fixedSplit(text, chunkSize, chunkOverlap);
    }

    private List<String> splitByMarkdownHeadings(String text) {
        Pattern pattern = java.util.regex.Pattern.compile("(?m)^#{1,6}\\s+.+$");
        Matcher matcher = pattern.matcher(text);
        List<Integer> starts = new ArrayList<>();
        while (matcher.find()) {
            starts.add(matcher.start());
        }
        if (starts.isEmpty()) {
            return List.of();
        }
        List<String> sections = new ArrayList<>();
        for (int i = 0; i < starts.size(); i++) {
            int start = starts.get(i);
            int end = i + 1 < starts.size() ? starts.get(i + 1) : text.length();
            String section = text.substring(start, end).trim();
            if (StringUtils.hasText(section)) {
                sections.add(section);
            }
        }
        return sections;
    }

    private List<String> flattenSegments(List<String> segments, int chunkSize, int chunkOverlap) {
        List<String> result = new ArrayList<>();
        StringBuilder builder = new StringBuilder();
        for (String segment : segments) {
            if (segment.length() > chunkSize) {
                if (builder.length() > 0) {
                    result.add(builder.toString().trim());
                    builder.setLength(0);
                }
                result.addAll(fixedSplit(segment, chunkSize, chunkOverlap));
                continue;
            }
            if (builder.length() > 0 && builder.length() + 1 + segment.length() > chunkSize) {
                result.add(builder.toString().trim());
                builder.setLength(0);
            }
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(segment);
        }
        if (builder.length() > 0) {
            result.add(builder.toString().trim());
        }
        return result;
    }

    private List<String> fixedSplit(String text, int chunkSize, int chunkOverlap) {
        List<String> chunks = new ArrayList<>();
        String normalized = text == null ? "" : text.trim();
        if (!StringUtils.hasText(normalized)) {
            return chunks;
        }
        int start = 0;
        while (start < normalized.length()) {
            int end = Math.min(start + chunkSize, normalized.length());
            chunks.add(normalized.substring(start, end));
            if (end >= normalized.length()) {
                break;
            }
            start = Math.max(end - chunkOverlap, start + 1);
        }
        return chunks;
    }

    private void batchInsertChunks(List<ChunkDO> chunkEntities) {
        String sql = """
                INSERT INTO t_chunk
                (id, knowledge_base_id, document_id, chunk_no, chunk_hash, original_content, effective_content,
                 token_count, embedding_status, enabled, manual_edited, created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        for (int start = 0; start < chunkEntities.size(); start += CHUNK_BATCH_SIZE) {
            int end = Math.min(start + CHUNK_BATCH_SIZE, chunkEntities.size());
            List<Object[]> batchArgs = chunkEntities.subList(start, end).stream()
                    .map(each -> new Object[]{
                            each.getId(),
                            each.getKnowledgeBaseId(),
                            each.getDocumentId(),
                            each.getChunkNo(),
                            each.getChunkHash(),
                            each.getOriginalContent(),
                            each.getEffectiveContent(),
                            each.getTokenCount(),
                            each.getEmbeddingStatus(),
                            each.getEnabled(),
                            each.getManualEdited(),
                            each.getCreatedBy(),
                            each.getUpdatedBy()
                    })
                    .toList();
            jdbcTemplate.batchUpdate(sql, batchArgs);
        }
    }

    private void batchAddVectorDocuments(List<Document> vectorDocuments) {
        for (int start = 0; start < vectorDocuments.size(); start += VECTOR_BATCH_SIZE) {
            int end = Math.min(start + VECTOR_BATCH_SIZE, vectorDocuments.size());
            chunkVectorStore.add(vectorDocuments.subList(start, end));
        }
    }

    private KnowledgeDocumentSplitServiceImpl currentSplitService() {
        return ApplicationContextHolder.getInstance() == null
                ? this
                : ApplicationContextHolder.getBean(KnowledgeDocumentSplitServiceImpl.class);
    }

    private String sanitizeSplitErrorMessage(String errorMessage) {
        if (!StringUtils.hasText(errorMessage)) {
            return DEFAULT_SPLIT_FAILURE_MESSAGE;
        }
        String normalized = errorMessage.replaceAll("\\s+", " ").trim();
        if (normalized.contains("batch size is invalid") || normalized.contains("should not be larger than 10")) {
            return BATCH_LIMIT_FAILURE_MESSAGE;
        }
        if (looksLikeStructuredUpstreamError(normalized)) {
            return DEFAULT_SPLIT_FAILURE_MESSAGE;
        }
        return normalized.length() <= 500 ? normalized : normalized.substring(0, 500);
    }

    private boolean looksLikeStructuredUpstreamError(String errorMessage) {
        return errorMessage.startsWith("400 - {")
                || errorMessage.startsWith("500 - {")
                || errorMessage.contains("\"error\":{")
                || errorMessage.contains("\"request_id\":");
    }

    private long elapsedMillis(long startNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }

    private record ChunkConfig(int chunkSize, int chunkOverlap) {
        private static final com.fasterxml.jackson.databind.ObjectMapper OBJECT_MAPPER = new com.fasterxml.jackson.databind.ObjectMapper();

        static ChunkConfig fromJson(String json) {
            try {
                com.fasterxml.jackson.databind.JsonNode node = OBJECT_MAPPER.readTree(json);
                int chunkSize = node.path("chunkSize").asInt();
                int chunkOverlap = node.path("chunkOverlap").asInt();
                if (chunkSize <= 0 || chunkOverlap < 0 || chunkOverlap >= chunkSize) {
                    throw new ClientException("chunkConfig 不合法");
                }
                return new ChunkConfig(chunkSize, chunkOverlap);
            } catch (java.io.IOException ex) {
                throw new ClientException("chunkConfig 解析失败");
            }
        }
    }

    private static final class NamedByteArrayResource extends ByteArrayResource {

        private final String filename;

        private NamedByteArrayResource(byte[] byteArray, String filename) {
            super(byteArray);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }
}
