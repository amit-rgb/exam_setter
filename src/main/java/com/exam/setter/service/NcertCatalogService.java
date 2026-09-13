package com.exam.setter.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class NcertCatalogService {
    private final JdbcTemplate jdbc;

    public NcertCatalogService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> books(String subject, String targetLevel, String corpusVersion) {
        StringBuilder sql = new StringBuilder("""
                SELECT book_code AS "bookCode", MAX(book_title) AS "bookTitle",
                       MAX(subject) AS subject, MAX(corpus_version) AS "corpusVersion",
                       COUNT(*) AS chapters
                FROM ncert_corpus_document
                WHERE status = 'COMPLETED'
                """);
        String normalizedVersion = blank(corpusVersion) ? null : corpusVersion.trim();
        String normalizedSubject = blank(subject) ? null : subject.trim().toUpperCase();
        String normalizedLevel = blank(targetLevel) ? null : targetLevel.trim().toUpperCase();
        if (normalizedVersion != null) sql.append(" AND corpus_version = ?");
        if (normalizedSubject != null) sql.append(" AND UPPER(subject) = ?");
        if (normalizedLevel != null) sql.append(" AND UPPER(class_level) = ?");
        sql.append(" GROUP BY book_code ORDER BY book_title, book_code");

        List<Object> params = new java.util.ArrayList<>();
        if (normalizedVersion != null) params.add(normalizedVersion);
        if (normalizedSubject != null) params.add(normalizedSubject);
        if (normalizedLevel != null) params.add(normalizedLevel);
        return jdbc.queryForList(sql.toString(), params.toArray());
    }

    public List<Map<String, Object>> chapters(String bookCode, String targetLevel, String corpusVersion) {
        StringBuilder sql = new StringBuilder("""
                SELECT document_key AS "documentKey", book_code AS "bookCode", book_title AS "bookTitle",
                       chapter_number AS "chapterNumber", chapter_title AS "chapterTitle",
                       subject, class_level AS "classLevel", corpus_version AS "corpusVersion",
                       chunk_count AS chunks, file_name AS "fileName", source_url AS "sourceUrl"
                FROM ncert_corpus_document
                WHERE status = 'COMPLETED' AND book_code = ?
                """);
        List<Object> params = new java.util.ArrayList<>();
        params.add(bookCode);
        if (!blank(targetLevel)) { sql.append(" AND UPPER(class_level) = ?"); params.add(targetLevel.trim().toUpperCase()); }
        if (!blank(corpusVersion)) { sql.append(" AND corpus_version = ?"); params.add(corpusVersion.trim()); }
        sql.append(" ORDER BY chapter_number NULLS LAST, chapter_title");
        return jdbc.queryForList(sql.toString(), params.toArray());
    }

    public Map<String, Object> summary(String corpusVersion) {
        String filter = blank(corpusVersion) ? "" : " WHERE corpus_version = '" + corpusVersion.replace("'", "''") + "'";
        Integer documents = jdbc.queryForObject("SELECT COUNT(*) FROM ncert_corpus_document" + filter, Integer.class);
        Integer completed = jdbc.queryForObject("SELECT COUNT(*) FROM ncert_corpus_document" + (filter.isBlank() ? " WHERE" : filter + " AND") + " status = 'COMPLETED'", Integer.class);
        Integer chunks = jdbc.queryForObject("SELECT COALESCE(SUM(chunk_count),0) FROM ncert_corpus_document" + filter, Integer.class);
        return Map.of("documents", documents == null ? 0 : documents, "completed", completed == null ? 0 : completed, "chunks", chunks == null ? 0 : chunks,
                "corpusVersion", blank(corpusVersion) ? "ALL" : corpusVersion);
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }
}
