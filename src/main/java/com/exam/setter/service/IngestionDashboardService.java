package com.exam.setter.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class IngestionDashboardService {

    private final JdbcTemplate jdbcTemplate;

    public IngestionDashboardService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> getDashboard() {
        List<Map<String, Object>> documents = jdbcTemplate.query("""
                SELECT
                    COALESCE(metadata->>'documentKey', metadata->>'fileName', id::text) AS document_key,
                    MAX(metadata->>'fileName') AS file_name,
                    MAX(metadata->>'source') AS source,
                    MAX(metadata->>'sourceType') AS source_type,
                    MAX(metadata->>'subject') AS subject,
                    MAX(metadata->>'targetLevels') AS target_levels,
                    MAX(metadata->>'targetLevel') AS target_level,
                    MAX(metadata->>'language') AS language,
                    MAX(metadata->>'chapterTitle') AS chapter_title,
                    MAX(metadata->>'topic') AS topic,
                    MAX(metadata->>'examId') AS exam_id,
                    MAX(metadata->>'year') AS paper_year,
                    MAX(metadata->>'paperName') AS paper_name,
                    COUNT(*) AS indexed_chunks
                FROM document_embeddings
                GROUP BY COALESCE(metadata->>'documentKey', metadata->>'fileName', id::text)
                ORDER BY MAX(metadata->>'fileName') NULLS LAST
                """, (rs, rowNum) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("documentKey", rs.getString("document_key"));
            row.put("fileName", rs.getString("file_name"));
            row.put("source", rs.getString("source"));
            row.put("sourceType", rs.getString("source_type"));
            row.put("subject", rs.getString("subject"));
            row.put("targetLevels", firstNonBlank(rs.getString("target_levels"), rs.getString("target_level")));
            row.put("language", rs.getString("language"));
            row.put("chapterTitle", rs.getString("chapter_title"));
            row.put("topic", rs.getString("topic"));
            row.put("examId", rs.getString("exam_id"));
            row.put("year", rs.getObject("paper_year"));
            row.put("paperName", rs.getString("paper_name"));
            row.put("indexedChunks", rs.getLong("indexed_chunks"));
            return row;
        });

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("totalDocuments", documents.size());
        response.put("totalChunks", documents.stream()
                .mapToLong(d -> ((Number) d.get("indexedChunks")).longValue()).sum());
        response.put("documents", documents);
        return response;
    }

    public Map<String, Object> getDocumentContent(String documentKey) {
        List<Map<String, Object>> chunks = jdbcTemplate.query("""
                SELECT
                    content,
                    metadata->>'fileName' AS file_name,
                    metadata->>'chunkIndex' AS chunk_index,
                    metadata->>'pageNumber' AS page_number,
                    metadata->>'sourceType' AS source_type
                FROM document_embeddings
                WHERE metadata->>'documentKey' = ?
                ORDER BY
                    NULLIF(metadata->>'pageNumber', '')::integer NULLS LAST,
                    NULLIF(metadata->>'chunkIndex', '')::integer NULLS LAST,
                    id
                """, (rs, rowNum) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("content", rs.getString("content"));
            row.put("fileName", rs.getString("file_name"));
            row.put("chunkIndex", rs.getString("chunk_index"));
            row.put("pageNumber", rs.getString("page_number"));
            row.put("sourceType", rs.getString("source_type"));
            return row;
        }, documentKey);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("documentKey", documentKey);
        response.put("chunkCount", chunks.size());
        response.put("chunks", chunks);
        return response;
    }

    private String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }
}
