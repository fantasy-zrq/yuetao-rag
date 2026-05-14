package com.rag.cn.yuetaoragbackend.dao.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.rag.cn.yuetaoragbackend.dao.projection.RetrievedChunk;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class ChunkVectorMapperSqlContractTests {

    @Test
    void shouldKeepSelectAliasesAlignedWithRetrievedChunkRecordOrder() throws IOException {
        String xml = new String(
                new ClassPathResource("mapper/ChunkVectorMapper.xml").getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
        List<String> expectedAliases = Arrays.stream(RetrievedChunk.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();

        List<String> aliases = Pattern.compile("\\bas\\s+([a-zA-Z0-9_]+)\\b")
                .matcher(xml)
                .results()
                .map(match -> match.group(1))
                .filter(expectedAliases::contains)
                .toList();

        assertThat(aliases).containsExactlyElementsOf(expectedAliases);
    }

    @Test
    void shouldCheckChunkLevelDepartmentAuthForSensitiveDocuments() throws IOException {
        String xml = new String(
                new ClassPathResource("mapper/ChunkVectorMapper.xml").getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);

        assertThat(xml)
                .as("sensitive document retrieval should enforce chunk-level department auth")
                .contains("t_chunk_department_auth");
    }
}
