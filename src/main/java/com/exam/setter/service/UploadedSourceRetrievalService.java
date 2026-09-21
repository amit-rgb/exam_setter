package com.exam.setter.service;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class UploadedSourceRetrievalService {
    private static final Logger log = LoggerFactory.getLogger(UploadedSourceRetrievalService.class);

    private static final List<String> KNOWLEDGE_TYPES = List.of("STUDY_NOTES", "REFERENCE", "TEXTBOOK");
    private final VectorStore vectorStore;

    public UploadedSourceRetrievalService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public List<Document> retrieveKnowledge(String subject, List<String> targetLevels, String query, int topK) {
        return retrieveKnowledge(subject, targetLevels, query, KNOWLEDGE_TYPES, topK, 0.30);
    }

    public List<Document> retrieveKnowledge(String subject, List<String> targetLevels, String query,
                                            List<String> sourceTypes, int topK) {
        return retrieveKnowledge(subject, targetLevels, query, sourceTypes, topK, 0.20);
    }

    private List<Document> retrieveKnowledge(String subject, List<String> targetLevels, String query,
                                             List<String> sourceTypes, int topK, double threshold) {
        return search(subject, targetLevels, query, sourceTypes, topK, threshold);
    }

    public List<Document> retrieveQuestionBankEvidence(String subject, List<String> targetLevels, String query, int topK) {
        return search(subject, targetLevels, query, List.of("QUESTION_BANK"), topK, 0.20);
    }

    public List<Document> retrieveSyllabusScope(String subject, List<String> targetLevels, String query, int topK) {
        return search(subject, targetLevels, query, List.of("SYLLABUS"), topK, 0.20);
    }

    public String citation(Document document) {
        Map<String, Object> metadata = document.getMetadata();
        String type = String.valueOf(metadata.getOrDefault("sourceType", "USER_UPLOAD"));
        String file = String.valueOf(metadata.getOrDefault("fileName", "uploaded source"));
        String subject = String.valueOf(metadata.getOrDefault("subject", ""));
        String level = String.valueOf(metadata.getOrDefault("targetLevel", ""));
        StringBuilder result = new StringBuilder(type).append(" — ").append(file);
        if (!subject.isBlank()) result.append(" — ").append(subject);
        if (!level.isBlank()) result.append(" — ").append(level);
        String chapter = String.valueOf(metadata.getOrDefault("chapterTitle", ""));
        String topic = String.valueOf(metadata.getOrDefault("topic", ""));
        if (!chapter.isBlank()) result.append(" — ").append(chapter);
        if (!topic.isBlank()) result.append(" — ").append(topic);
        return result.toString();
    }

    private List<Document> search(String subject, List<String> targetLevels, String query,
                                  List<String> sourceTypes, int topK, double threshold) {
        StringBuilder filter = new StringBuilder("source == 'USER_UPLOAD'")
                .append(" && subject == '").append(escape(subject == null ? "" : subject.trim().toLowerCase())).append("'")
                .append(" && sourceType in [");
        for (int i = 0; i < sourceTypes.size(); i++) {
            if (i > 0) filter.append(", ");
            filter.append("'").append(escape(sourceTypes.get(i))).append("'");
        }
        filter.append("]");

        /*
         * Do not push target-level matching into the vector-store filter.
         * Existing uploads may store levels as targetLevel, targetLevels,
         * comma-separated values, or legacy JSON-like metadata. Filtering
         * those representations in Java makes retrieval backward compatible.
         */
        int retrievalK = Math.max(Math.max(1, topK), Math.min(200, Math.max(20, topK * 4)));
        log.info("Uploaded retrieval: subject={}, levels={}, sourceTypes={}, topK={}, threshold={}", subject, targetLevels, sourceTypes, topK, threshold);
        List<Document> candidates = vectorStore.similaritySearch(SearchRequest.builder()
                .query(query == null || query.isBlank() ? subject + " concepts examples" : query)
                .topK(retrievalK)
                .similarityThreshold(threshold)
                .filterExpression(filter.toString())
                .build());

        List<String> levels = normalizeLevels(targetLevels);
        if (levels.isEmpty()) { log.info("Uploaded retrieval candidates={} (no level filter)", candidates.size()); return candidates.stream().limit(Math.max(1, topK)).toList(); }

        List<Document> matched = candidates.stream()
                .filter(document -> matchesAnyTargetLevel(document, levels))
                .limit(Math.max(1, topK))
                .toList();

        log.info("Uploaded retrieval candidates={}, matchedLevels={}, requestedLevels={}", candidates.size(), matched.size(), levels);
        return matched;
    }

    private boolean matchesAnyTargetLevel(Document document, List<String> requestedLevels) {
        Map<String, Object> metadata = document.getMetadata();
        String targetLevel = String.valueOf(metadata.getOrDefault("targetLevel", ""));
        String targetLevels = String.valueOf(metadata.getOrDefault("targetLevels", ""));
        String combined = (targetLevel + "," + targetLevels).toUpperCase();
        return requestedLevels.stream().anyMatch(level -> containsLevelToken(combined, level));
    }

    private boolean containsLevelToken(String value, String level) {
        if (value == null || value.isBlank()) return false;
        String normalized = value.toUpperCase().replaceAll("[\\[\\]\"'\\s]", "");
        for (String token : normalized.split(",")) {
            if (level.equals(token)) return true;
        }
        return normalized.contains(level);
    }

    private List<String> normalizeLevels(List<String> values) {
        if (values == null) return List.of();
        List<String> levels = new ArrayList<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) levels.add(value.trim().toUpperCase());
        }
        return levels.stream().distinct().toList();
    }

    private String escape(String value) {
        return value.replace("'", "\\'");
    }
}
