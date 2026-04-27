package com.rag.cn.yuetaoragbackend.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.rag.cn.yuetaoragbackend.config.enums.DeleteFlagEnum;
import com.rag.cn.yuetaoragbackend.config.enums.ParseStatusEnum;
import com.rag.cn.yuetaoragbackend.dao.entity.ChunkDO;
import com.rag.cn.yuetaoragbackend.dao.entity.KnowledgeDocumentDO;
import com.rag.cn.yuetaoragbackend.dao.mapper.ChunkMapper;
import com.rag.cn.yuetaoragbackend.dao.mapper.KnowledgeDocumentMapper;
import com.rag.cn.yuetaoragbackend.framework.exception.ClientException;
import com.rag.cn.yuetaoragbackend.service.file.FileService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author zrq
 * 2026/04/27 10:20
 */
@Service
@RequiredArgsConstructor
public class KnowledgeDocumentSplitExecutionService {

    public static final String SPLIT_TOPIC = "yuetao-rag-chunk_topic";

    public static final String SPLIT_CONSUMER_GROUP = "yuetao-rag-chunk-consumer_group";

    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final ChunkMapper chunkMapper;
    private final FileService fileService;
    private final PgVectorStore chunkVectorStore;

    @Transactional(rollbackFor = Exception.class)
    public void processSplit(Long documentId) {
        KnowledgeDocumentDO documentDO = knowledgeDocumentMapper.selectOne(Wrappers.<KnowledgeDocumentDO>lambdaQuery()
                .eq(KnowledgeDocumentDO::getId, documentId)
                .eq(KnowledgeDocumentDO::getDeleteFlag, DeleteFlagEnum.NORMAL.getCode()));
        if (documentDO == null) {
            throw new ClientException("文档不存在或已删除：" + documentId);
        }
        byte[] content = fileService.getObject(documentDO.getStorageBucket(), documentDO.getStorageKey());
        String text = parseDocument(content, documentDO.getTitle());
        List<String> chunkTexts = splitTextByMode(text, documentDO.getChunkMode(), documentDO.getChunkConfig());
        if (chunkTexts.isEmpty()) {
            throw new ClientException("切片结果为空：" + documentId);
        }

        List<Document> vectorDocuments = new ArrayList<>();
        for (int i = 0; i < chunkTexts.size(); i++) {
            String chunkText = chunkTexts.get(i);
            long chunkId = IdWorker.getId();
            ChunkDO chunkDO = new ChunkDO()
                    .setKnowledgeBaseId(documentDO.getKnowledgeBaseId())
                    .setDocumentId(documentId)
                    .setChunkNo(i)
                    .setChunkHash(cn.hutool.crypto.digest.DigestUtil.sha256Hex(chunkText))
                    .setOriginalContent(chunkText)
                    .setEffectiveContent(chunkText)
                    .setTokenCount(chunkText.length())
                    .setEmbeddingStatus("SUCCESS")
                    .setEnabled(Boolean.TRUE)
                    .setManualEdited(Boolean.FALSE)
                    .setCreatedBy(documentDO.getUpdatedBy())
                    .setUpdatedBy(documentDO.getUpdatedBy());
            chunkDO.setId(chunkId);
            chunkMapper.insert(chunkDO);

            vectorDocuments.add(Document.builder()
                    .id(String.valueOf(chunkId))
                    .text(chunkText)
                    .metadata(Map.of(
                            "document_id", String.valueOf(documentId),
                            "chunk_no", i,
                            "collection_name", documentDO.getStorageBucket()))
                    .build());
        }

        chunkVectorStore.add(vectorDocuments);
        KnowledgeDocumentDO successDO = new KnowledgeDocumentDO();
        successDO.setId(documentId);
        successDO.setParseStatus(ParseStatusEnum.SUCCESS.getCode());
        successDO.setUpdatedBy(documentDO.getUpdatedBy());
        knowledgeDocumentMapper.updateById(successDO);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void markSplitFailed(Long documentId) {
        KnowledgeDocumentDO failedDO = new KnowledgeDocumentDO();
        failedDO.setId(documentId);
        failedDO.setParseStatus(ParseStatusEnum.FAILED.getCode());
        knowledgeDocumentMapper.updateById(failedDO);
    }

    private String parseDocument(byte[] content, String filename) {
        List<Document> documents = new TikaDocumentReader(new NamedByteArrayResource(content, filename)).get();
        return documents.stream()
                .map(Document::getText)
                .filter(org.springframework.util.StringUtils::hasText)
                .collect(java.util.stream.Collectors.joining("\n"));
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
                .filter(org.springframework.util.StringUtils::hasText)
                .toList();
        if (!paragraphs.isEmpty()) {
            return flattenSegments(paragraphs, chunkSize, chunkOverlap);
        }
        List<String> sentences = java.util.Arrays.stream(text.split("(?<=[。！？.!?])"))
                .map(String::trim)
                .filter(org.springframework.util.StringUtils::hasText)
                .toList();
        if (!sentences.isEmpty()) {
            return flattenSegments(sentences, chunkSize, chunkOverlap);
        }
        return fixedSplit(text, chunkSize, chunkOverlap);
    }

    private List<String> splitByMarkdownHeadings(String text) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(?m)^#{1,6}\\s+.+$");
        java.util.regex.Matcher matcher = pattern.matcher(text);
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
            if (org.springframework.util.StringUtils.hasText(section)) {
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
        if (!org.springframework.util.StringUtils.hasText(normalized)) {
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
