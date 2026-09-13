package com.exam.setter.service;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class NcertRetrievalService {
    private final VectorStore vectorStore;
    private final String defaultCorpusVersion;

    public NcertRetrievalService(VectorStore vectorStore,
                                 @Value("${app.ncert.ingestion.corpus-version:2026}") String defaultCorpusVersion) {
        this.vectorStore = vectorStore;
        this.defaultCorpusVersion = defaultCorpusVersion;
    }

    public List<Document> retrieve(String subject, List<String> targetLevels, String query,
                                   String corpusVersion, String bookCode, Integer chapterNumber, int topK) {
        String normalizedSubject = normalize(subject);
        List<String> levels = normalizeLevels(targetLevels);
        String version = blank(corpusVersion) ? defaultCorpusVersion : corpusVersion.trim();

        StringBuilder filter = new StringBuilder()
                .append("source == 'NCERT' && sourceType == 'TEXTBOOK'")
                .append(" && subject == '").append(escape(normalizedSubject)).append("'")
                .append(" && corpusVersion == '").append(escape(version)).append("'");

        if (!levels.isEmpty()) {
            filter.append(" && classLevel in [");
            for (int i = 0; i < levels.size(); i++) {
                if (i > 0) filter.append(", ");
                filter.append("'").append(escape(levels.get(i))).append("'");
            }
            filter.append("]");
        }
        if (!blank(bookCode)) {
            filter.append(" && bookCode == '").append(escape(bookCode.trim())).append("'");
        }
        if (chapterNumber != null) {
            filter.append(" && chapterNumber == ").append(chapterNumber);
        }

        int candidateCount = Math.min(120, Math.max(topK * 5, 20));
        List<Document> candidates = vectorStore.similaritySearch(SearchRequest.builder()
                .query(query == null || query.isBlank() ? normalizedSubject + " concepts principles examples" : query)
                .topK(candidateCount)
                .similarityThreshold(0.20)
                .filterExpression(filter.toString())
                .build());

        return rerank(candidates, levels, bookCode, chapterNumber, topK);
    }

    private List<Document> rerank(List<Document> candidates, List<String> levels,
                                  String requestedBook, Integer requestedChapter, int topK) {
        if (candidates.isEmpty()) return List.of();
        Map<String, Integer> perDocument = new LinkedHashMap<>();
        return candidates.stream()
                .sorted(Comparator.comparingDouble((Document d) -> rerankScore(d, levels, requestedBook, requestedChapter)).reversed())
                .filter(d -> {
                    String key = String.valueOf(d.getMetadata().getOrDefault("documentKey", d.getMetadata().getOrDefault("fileName", d.getId())));
                    int count = perDocument.getOrDefault(key, 0);
                    if (count >= 3) return false;
                    perDocument.put(key, count + 1);
                    return true;
                })
                .limit(Math.max(1, topK))
                .toList();
    }

    private double rerankScore(Document document, List<String> levels, String requestedBook, Integer requestedChapter) {
        Double semantic = document.getScore();
        double score = semantic == null ? 0.0 : semantic;
        Map<String, Object> metadata = document.getMetadata();
        if (!blank(requestedBook) && requestedBook.equalsIgnoreCase(String.valueOf(metadata.get("bookCode")))) score += 0.15;
        if (requestedChapter != null && Objects.equals(toInteger(metadata.get("chapterNumber")), requestedChapter)) score += 0.20;
        if (!levels.isEmpty() && levels.contains(normalize(String.valueOf(metadata.get("classLevel"))))) score += 0.10;
        return score;
    }

    private Integer toInteger(Object value) {
        if (value instanceof Number number) return number.intValue();
        try { return value == null ? null : Integer.valueOf(value.toString()); }
        catch (NumberFormatException ignored) { return null; }
    }

    public String citation(Document document) {
        Map<String, Object> m = document.getMetadata();
        String book = String.valueOf(m.getOrDefault("bookTitle", m.getOrDefault("bookCode", "NCERT")));
        String chapter = String.valueOf(m.getOrDefault("chapterTitle", ""));
        String page = String.valueOf(m.getOrDefault("pageNumber", ""));
        String source = String.valueOf(m.getOrDefault("sourceUrl", ""));
        return book + (chapter.isBlank() ? "" : " — " + chapter)
                + (page.isBlank() ? "" : " — page " + page)
                + (source.isBlank() ? "" : " — " + source);
    }

    private List<String> normalizeLevels(List<String> values) {
        if (values == null) return List.of();
        return values.stream().filter(Objects::nonNull).map(this::normalize).filter(v -> !v.isBlank()).distinct().toList();
    }

    private String normalize(String value) { return value == null ? "" : value.trim().toUpperCase(); }
    private boolean blank(String value) { return value == null || value.isBlank(); }
    private String escape(String value) { return value == null ? "" : value.replace("'", "\\'"); }
}
