package com.exam.setter.service;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class QuestionSimilarityGuardService {

    private final VectorStore vectorStore;

    public QuestionSimilarityGuardService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public boolean isTooSimilarToKnowledge(String questionText, String subject) {
        return !search(questionText, "subject == '" + escape(subject) + "'", 0.92).isEmpty();
    }

    public boolean isTooSimilarToPyq(String questionText, String examId) {
        String filter = examId == null || examId.isBlank()
                ? "sourceType == 'PREVIOUS_YEAR_PAPER'"
                : "sourceType == 'PREVIOUS_YEAR_PAPER' && examId == '" + escape(examId) + "'";
        return !search(questionText, filter, 0.94).isEmpty();
    }

    private List<Document> search(String text, String filter, double threshold) {
        if (text == null || text.isBlank()) return List.of();
        return vectorStore.similaritySearch(SearchRequest.builder()
                .query(text)
                .topK(1)
                .similarityThreshold(threshold)
                .filterExpression(filter)
                .build());
    }

    private String escape(String value) { return value == null ? "" : value.replace("'", "\\'"); }
}
