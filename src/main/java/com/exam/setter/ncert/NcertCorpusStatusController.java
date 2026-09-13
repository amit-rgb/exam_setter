package com.exam.setter.ncert;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/ncert")
public class NcertCorpusStatusController {
    private final JdbcTemplate jdbc;

    public NcertCorpusStatusController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        List<Map<String, Object>> byStatus = jdbc.queryForList("""
                SELECT status, COUNT(*) AS documents, COALESCE(SUM(chunk_count), 0) AS chunks
                FROM ncert_corpus_document
                GROUP BY status
                ORDER BY status
                """);
        Integer documents = jdbc.queryForObject("SELECT COUNT(*) FROM ncert_corpus_document", Integer.class);
        Integer chunks = jdbc.queryForObject("SELECT COALESCE(SUM(chunk_count), 0) FROM ncert_corpus_document", Integer.class);
        return Map.of("documents", documents == null ? 0 : documents, "chunks", chunks == null ? 0 : chunks, "byStatus", byStatus);
    }
}
