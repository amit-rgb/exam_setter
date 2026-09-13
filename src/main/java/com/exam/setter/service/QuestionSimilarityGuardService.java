package com.exam.setter.service;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Guard against generating questions that are too close to retrieved source
 * material or known exam questions. This foundation exposes a reusable hook;
 * the next increment can add a dedicated PYQ collection and threshold tuning.
 */
@Service
public class QuestionSimilarityGuardService {

    private final VectorStore vectorStore;

    public QuestionSimilarityGuardService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public boolean isTooSimilarToKnowledge(String questionText, String subject) {
        if (questionText == null || questionText.isBlank()) {
            return false;
        }

        String filter = String.format("subject == '%s'", subject.trim().toLowerCase());
        List<Document> matches = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(questionText)
                        .topK(1)
                        .similarityThreshold(0.90)
                        .filterExpression(filter)
                        .build()
        );

        return !matches.isEmpty();
    }
}
