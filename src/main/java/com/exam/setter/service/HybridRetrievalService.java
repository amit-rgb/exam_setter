package com.exam.setter.service;

import com.fasterxml.jackson.core.type.TypeReference;
import java.util.concurrent.atomic.AtomicBoolean;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.document.Document;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class HybridRetrievalService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final AtomicBoolean indexesReady = new AtomicBoolean(false);

    public HybridRetrievalService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public List<Document> keywordSearch(String query, String source, String subject,
                                        List<String> sourceTypes, int limit,
                                        String corpusVersion, String bookCode, Integer chapterNumber) {
        if (query == null || query.isBlank()) return List.of();
        ensureIndexes();

        StringBuilder sql = new StringBuilder("""
            SELECT id, content, metadata::text AS metadata_text
              FROM document_embeddings
             WHERE metadata->>'source' = ?
               AND LOWER(metadata->>'subject') = LOWER(?)
               AND to_tsvector('simple', COALESCE(content,'')) @@ plainto_tsquery('simple', ?)
            """);
        List<Object> args = new ArrayList<>(List.of(source, subject == null ? "" : subject.trim().toLowerCase(), query));
        if (sourceTypes != null && !sourceTypes.isEmpty()) {
            sql.append(" AND metadata->>'sourceType' IN (")
               .append(String.join(",", sourceTypes.stream().map(v -> "?").toList())).append(")");
            args.addAll(sourceTypes);
        }
        if (corpusVersion != null && !corpusVersion.isBlank()) {
            sql.append(" AND (metadata->>'corpusVersion' = ? OR metadata->>'corpusVersion' IS NULL)");
            args.add(corpusVersion);
        }
        if (bookCode != null && !bookCode.isBlank()) {
            sql.append(" AND metadata->>'bookCode' = ?");
            args.add(bookCode);
        }
        if (chapterNumber != null) {
            sql.append(" AND metadata->>'chapterNumber' = ?");
            args.add(String.valueOf(chapterNumber));
        }
        sql.append(" ORDER BY ts_rank_cd(to_tsvector('simple', COALESCE(content,'')), plainto_tsquery('simple', ?)) DESC LIMIT ?");
        args.add(query);
        args.add(Math.max(1, limit));

        return jdbcTemplate.query(sql.toString(), args.toArray(), (rs, rowNum) -> {
            Map<String,Object> metadata;
            try {
                metadata = objectMapper.readValue(rs.getString("metadata_text"),
                        new TypeReference<Map<String,Object>>() {});
            } catch (Exception ex) {
                metadata = new LinkedHashMap<>();
            }
            return new Document(rs.getString("id"), rs.getString("content"), metadata);
        });
    }

    private void ensureIndexes() {
        if (indexesReady.get()) return;
        try {
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_document_embeddings_fts ON document_embeddings USING GIN (to_tsvector('simple', COALESCE(content,'')))");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_document_embeddings_document_key ON document_embeddings ((metadata->>'documentKey'))");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_document_embeddings_subject ON document_embeddings ((metadata->>'subject'))");
            indexesReady.set(true);
        } catch (Exception ignored) {
            // Vector-store initialization may create the table after application startup.
            // Retry lazily on the next keyword-search request.
        }
    }

    public List<Document> expandContext(List<Document> seeds, int neighbors, int maxResults) {
        if (seeds == null || seeds.isEmpty()) return List.of();
        Map<String, Document> result = new LinkedHashMap<>();
        for (Document seed : seeds) {
            add(result, seed);
            String key = String.valueOf(seed.getMetadata().getOrDefault("documentKey", ""));
            Integer index = toInteger(seed.getMetadata().get("chunkIndex"));
            if (key.isBlank() || index == null) continue;
            int from = Math.max(0, index - Math.max(0, neighbors));
            int to = index + Math.max(0, neighbors);
            try {
                List<Document> adjacent = jdbcTemplate.query("""
                    SELECT id, content, metadata::text AS metadata_text
                      FROM document_embeddings
                     WHERE metadata->>'documentKey' = ?
                       AND CASE WHEN metadata->>'chunkIndex' ~ '^[0-9]+
        Map<String, Candidate> candidates = new LinkedHashMap<>();
        for (Document d : semantic) {
            candidates.computeIfAbsent(key(d), k -> new Candidate(d))
                    .semantic = d.getScore() == null ? 0.0 : d.getScore();
        }
        for (Document d : keyword) {
            candidates.computeIfAbsent(key(d), k -> new Candidate(d)).keyword = lexicalScore(query, d.getText());
        }
        return candidates.values().stream()
                .sorted(Comparator.comparingDouble((Candidate c) -> c.finalScore(query)).reversed())
                .limit(Math.max(1, topK))
                .map(c -> c.document)
                .toList();
    }

    private double lexicalScore(String query, String text) {
        if (query == null || text == null) return 0;
        List<String> terms = tokenize(query);
        if (terms.isEmpty()) return 0;
        String normalized = text.toLowerCase();
        long hits = terms.stream().filter(normalized::contains).distinct().count();
        return hits / (double) terms.size();
    }

    private List<String> tokenize(String value) {
        return List.of(value.toLowerCase().replaceAll("[^\\p{L}\\p{Nd}]+", " ").trim().split("\\s+"));
    }

    private String key(Document d) {
        Object id = d.getId();
        if (id != null) return id.toString();
        return d.getMetadata().getOrDefault("documentKey","") + "|" + d.getText().hashCode();
    }

    private static class Candidate {
        private final Document document;
        private double semantic;
        private double keyword;

        private Candidate(Document document) { this.document = document; }

        private double finalScore(String query) {
            return (0.70 * semantic) + (0.30 * keyword);
        }
    }
}

                                THEN (metadata->>'chunkIndex')::integer ELSE NULL END BETWEEN ? AND ?
                     ORDER BY CASE WHEN metadata->>'chunkIndex' ~ '^[0-9]+
        Map<String, Candidate> candidates = new LinkedHashMap<>();
        for (Document d : semantic) {
            candidates.computeIfAbsent(key(d), k -> new Candidate(d))
                    .semantic = d.getScore() == null ? 0.0 : d.getScore();
        }
        for (Document d : keyword) {
            candidates.computeIfAbsent(key(d), k -> new Candidate(d)).keyword = lexicalScore(query, d.getText());
        }
        return candidates.values().stream()
                .sorted(Comparator.comparingDouble((Candidate c) -> c.finalScore(query)).reversed())
                .limit(Math.max(1, topK))
                .map(c -> c.document)
                .toList();
    }

    private double lexicalScore(String query, String text) {
        if (query == null || text == null) return 0;
        List<String> terms = tokenize(query);
        if (terms.isEmpty()) return 0;
        String normalized = text.toLowerCase();
        long hits = terms.stream().filter(normalized::contains).distinct().count();
        return hits / (double) terms.size();
    }

    private List<String> tokenize(String value) {
        return List.of(value.toLowerCase().replaceAll("[^\\p{L}\\p{Nd}]+", " ").trim().split("\\s+"));
    }

    private String key(Document d) {
        Object id = d.getId();
        if (id != null) return id.toString();
        return d.getMetadata().getOrDefault("documentKey","") + "|" + d.getText().hashCode();
    }

    private static class Candidate {
        private final Document document;
        private double semantic;
        private double keyword;

        private Candidate(Document document) { this.document = document; }

        private double finalScore(String query) {
            return (0.70 * semantic) + (0.30 * keyword);
        }
    }
}

                                   THEN (metadata->>'chunkIndex')::integer ELSE NULL END
                    """, (rs, rowNum) -> {
                    Map<String,Object> metadata;
                    try {
                        metadata = objectMapper.readValue(rs.getString("metadata_text"), new TypeReference<Map<String,Object>>() {});
                    } catch (Exception ex) {
                        metadata = new LinkedHashMap<>();
                    }
                    return new Document(rs.getString("id"), rs.getString("content"), metadata);
                }, key, from, to);
                adjacent.forEach(d -> add(result, d));
            } catch (Exception ignored) {
                // Context expansion is an enhancement; retrieval must remain available
                // when a legacy vector table has incomplete chunk metadata.
            }
            if (result.size() >= maxResults) break;
        }
        return new ArrayList<>(result.values()).stream().limit(Math.max(1, maxResults)).toList();
    }

    private void add(Map<String, Document> target, Document document) {
        if (document == null) return;
        String key = document.getId() == null
                ? String.valueOf(document.getMetadata().getOrDefault("documentKey", "")) + "|" + document.getText().hashCode()
                : document.getId().toString();
        target.putIfAbsent(key, document);
    }

    private Integer toInteger(Object value) {
        try { return value == null ? null : Integer.valueOf(String.valueOf(value)); }
        catch (NumberFormatException ignored) { return null; }
    }

    public List<Document> merge(String query, List<Document> semantic, List<Document> keyword, int topK) {
        Map<String, Candidate> candidates = new LinkedHashMap<>();
        for (Document d : semantic) {
            candidates.computeIfAbsent(key(d), k -> new Candidate(d))
                    .semantic = d.getScore() == null ? 0.0 : d.getScore();
        }
        for (Document d : keyword) {
            candidates.computeIfAbsent(key(d), k -> new Candidate(d)).keyword = lexicalScore(query, d.getText());
        }
        return candidates.values().stream()
                .sorted(Comparator.comparingDouble((Candidate c) -> c.finalScore(query)).reversed())
                .limit(Math.max(1, topK))
                .map(c -> c.document)
                .toList();
    }

    private double lexicalScore(String query, String text) {
        if (query == null || text == null) return 0;
        List<String> terms = tokenize(query);
        if (terms.isEmpty()) return 0;
        String normalized = text.toLowerCase();
        long hits = terms.stream().filter(normalized::contains).distinct().count();
        return hits / (double) terms.size();
    }

    private List<String> tokenize(String value) {
        return List.of(value.toLowerCase().replaceAll("[^\\p{L}\\p{Nd}]+", " ").trim().split("\\s+"));
    }

    private String key(Document d) {
        Object id = d.getId();
        if (id != null) return id.toString();
        return d.getMetadata().getOrDefault("documentKey","") + "|" + d.getText().hashCode();
    }

    private static class Candidate {
        private final Document document;
        private double semantic;
        private double keyword;

        private Candidate(Document document) { this.document = document; }

        private double finalScore(String query) {
            return (0.70 * semantic) + (0.30 * keyword);
        }
    }
}
