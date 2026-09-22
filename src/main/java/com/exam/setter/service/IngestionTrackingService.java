package com.exam.setter.service;

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class IngestionTrackingService {

    private final JdbcTemplate jdbcTemplate;

    public IngestionTrackingService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    void initialize() {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS ingestion_documents (
                document_key VARCHAR(200) PRIMARY KEY,
                file_name VARCHAR(500),
                source VARCHAR(80),
                source_type VARCHAR(80),
                subject VARCHAR(200),
                target_levels VARCHAR(1000),
                status VARCHAR(40) NOT NULL,
                pipeline_version VARCHAR(40),
                chunk_version VARCHAR(40),
                content_hash VARCHAR(128),
                total_chunks INTEGER DEFAULT 0,
                total_pages INTEGER DEFAULT 0,
                error_message TEXT,
                started_at TIMESTAMPTZ,
                completed_at TIMESTAMPTZ,
                updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
            )
            """);
    }

    public void start(String key, String fileName, String source, String sourceType,
                      String subject, List<String> levels, String pipelineVersion,
                      String chunkVersion, String contentHash) {
        jdbcTemplate.update("""
            INSERT INTO ingestion_documents
            (document_key,file_name,source,source_type,subject,target_levels,status,pipeline_version,chunk_version,content_hash,started_at,updated_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
            ON CONFLICT (document_key) DO UPDATE SET
              file_name=EXCLUDED.file_name, source=EXCLUDED.source, source_type=EXCLUDED.source_type,
              subject=EXCLUDED.subject, target_levels=EXCLUDED.target_levels, status='PARSING',
              pipeline_version=EXCLUDED.pipeline_version, chunk_version=EXCLUDED.chunk_version,
              content_hash=EXCLUDED.content_hash, error_message=NULL,
              started_at=CURRENT_TIMESTAMP, completed_at=NULL, updated_at=CURRENT_TIMESTAMP
            """, key, fileName, source, sourceType, subject,
                String.join(",", levels), pipelineVersion, chunkVersion, contentHash);
    }

    public void status(String key, String status) {
        jdbcTemplate.update("UPDATE ingestion_documents SET status=?, updated_at=CURRENT_TIMESTAMP WHERE document_key=?",
                status, key);
    }

    public void complete(String key, int chunks, int pages) {
        jdbcTemplate.update("""
            UPDATE ingestion_documents
               SET status='COMPLETED', total_chunks=?, total_pages=?,
                   completed_at=CURRENT_TIMESTAMP, updated_at=CURRENT_TIMESTAMP
             WHERE document_key=?
            """, chunks, pages, key);
    }

    public void fail(String key, String message) {
        jdbcTemplate.update("""
            UPDATE ingestion_documents
               SET status='FAILED', error_message=?, updated_at=CURRENT_TIMESTAMP
             WHERE document_key=?
            """, truncate(message, 4000), key);
    }

    public boolean existsCompleted(String key, String hash, String chunkVersion) {
        Integer count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM ingestion_documents
             WHERE document_key=? AND status='COMPLETED'
               AND content_hash=? AND chunk_version=?
            """, Integer.class, key, hash, chunkVersion);
        return count != null && count > 0;
    }

    public List<Map<String, Object>> list() {
        return jdbcTemplate.queryForList("""
            SELECT document_key AS "documentKey", file_name AS "fileName", source, source_type AS "sourceType",
                   subject, target_levels AS "targetLevels", status, pipeline_version AS "pipelineVersion",
                   chunk_version AS "chunkVersion", content_hash AS "contentHash",
                   total_chunks AS "totalChunks", total_pages AS "totalPages",
                   error_message AS "errorMessage", started_at AS "startedAt", completed_at AS "completedAt"
              FROM ingestion_documents
             ORDER BY updated_at DESC
            """);
    }

    public String createJob() {
        return UUID.randomUUID().toString();
    }

    private String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
